package com.ravi.inventory.application.service;

import com.ravi.inventory.application.port.out.DistributedLockPort;
import com.ravi.inventory.application.port.out.DistributedLockPort.LockToken;
import com.ravi.inventory.domain.model.InventoryItem;
import com.ravi.inventory.domain.model.StockOperationResult;
import com.ravi.inventory.domain.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Service
public class InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);

    private final InventoryRepository inventoryRepository;
    private final DistributedLockPort distributedLock;
    private final Map<String, Integer> activeOrderReservations = new ConcurrentHashMap<>();

    public InventoryReservationService(InventoryRepository inventoryRepository, DistributedLockPort distributedLock) {
        this.inventoryRepository = inventoryRepository;
        this.distributedLock = distributedLock;
    }

    public Optional<InventoryItem> getItem(String productId) {
        return inventoryRepository.findById(productId);
    }

    public boolean reserveStock(String productId, int quantity) {
        return reserveStockDetailed(productId, quantity).success();
    }

    public StockOperationResult reserveStockDetailed(String productId, int quantity) {
        return reserveInternal(null, productId, quantity);
    }

    public StockOperationResult reserveForOrder(String orderId, String productId, int quantity) {
        return reserveInternal(orderId, productId, quantity);
    }

    private StockOperationResult reserveInternal(String orderId, String productId, int quantity) {
        if (quantity <= 0) {
            return new StockOperationResult(false, productId, quantity, 0, 0, 0, 0, 0,
                    "Requested quantity must be greater than zero");
        }

        String lockKey = "lock:stock:" + productId;
        LockToken lockToken = distributedLock.acquire(lockKey, Duration.ofSeconds(5));

        if (!lockToken.acquired()) {
            log.error("Could not acquire lock for product {}. Stock reservation aborted due to contention.", productId);
            return new StockOperationResult(false, productId, quantity, 0, 0, 0, 0, 0,
                    "Distributed lock contention for product " + productId);
        }

        try {
            Optional<InventoryItem> itemOpt = inventoryRepository.findById(productId);
            if (itemOpt.isEmpty()) {
                log.warn("Product {} not found in inventory catalog", productId);
                return new StockOperationResult(false, productId, quantity, 0, 0, 0, 0, 0,
                        "Product " + productId + " not found in inventory catalog");
            }

            InventoryItem item = itemOpt.get();
            if (item.availableQuantity() < quantity) {
                String reason = String.format("Insufficient stock for %s: requested %d, available %d",
                        productId, quantity, item.availableQuantity());
                log.warn(reason);
                return new StockOperationResult(
                        false,
                        productId,
                        quantity,
                        0,
                        item.availableQuantity(),
                        item.availableQuantity(),
                        item.reservedQuantity(),
                        item.reservedQuantity(),
                        reason
                );
            }

            // Perform atomic stock reduction
            InventoryItem updated = new InventoryItem(
                    item.productId(),
                    item.name(),
                    item.availableQuantity() - quantity,
                    item.reservedQuantity() + quantity
            );
            inventoryRepository.save(updated);

            if (orderId != null && !orderId.isBlank()) {
                activeOrderReservations.merge(allocationKey(orderId, productId), quantity, Integer::sum);
            }

            log.info("Reserved {} units of {}. Remaining available: {}",
                    quantity, productId, updated.availableQuantity());
            return new StockOperationResult(
                    true,
                    productId,
                    quantity,
                    quantity,
                    item.availableQuantity(),
                    updated.availableQuantity(),
                    item.reservedQuantity(),
                    updated.reservedQuantity(),
                    "RESERVED"
            );
        } finally {
            distributedLock.release(lockToken);
        }
    }

    /**
     * Compensating transaction for Saga orchestration:
     * Releases previously reserved stock back to available stock.
     */
    public boolean releaseStock(String productId, int quantity) {
        return releaseInternal(null, productId, quantity).success();
    }

    /**
     * Order-scoped compensating release: only releases stock if this order actually holds
     * an active reservation, preventing rejected or duplicate cancellations from mutating
     * other orders' reserved stock.
     */
    public StockOperationResult releaseForOrder(String orderId, String productId, int quantity) {
        return releaseInternal(orderId, productId, quantity);
    }

    private StockOperationResult releaseInternal(String orderId, String productId, int quantity) {
        if (quantity <= 0) {
            return new StockOperationResult(false, productId, quantity, 0, 0, 0, 0, 0,
                    "Release quantity must be greater than zero");
        }

        String lockKey = "lock:stock:" + productId;
        LockToken lockToken = distributedLock.acquire(lockKey, Duration.ofSeconds(5));

        if (!lockToken.acquired()) {
            log.error("Could not acquire lock for product {}. Stock compensation release aborted due to contention.", productId);
            return new StockOperationResult(false, productId, quantity, 0, 0, 0, 0, 0,
                    "Distributed lock contention during compensation release for " + productId);
        }

        try {
            Optional<InventoryItem> itemOpt = inventoryRepository.findById(productId);
            if (itemOpt.isEmpty()) {
                log.warn("Product {} not found in inventory catalog during compensation release", productId);
                return new StockOperationResult(false, productId, quantity, 0, 0, 0, 0, 0,
                        "Product " + productId + " not found in inventory catalog");
            }

            InventoryItem item = itemOpt.get();
            int targetQuantity = quantity;
            if (orderId != null && !orderId.isBlank()) {
                Integer trackedQty = activeOrderReservations.remove(allocationKey(orderId, productId));
                if (trackedQty == null || trackedQty <= 0) {
                    log.info("No active stock reservation found for order {} product {}; skipping compensation release.",
                            orderId, productId);
                    return new StockOperationResult(
                            false,
                            productId,
                            quantity,
                            0,
                            item.availableQuantity(),
                            item.availableQuantity(),
                            item.reservedQuantity(),
                            item.reservedQuantity(),
                            "No active reservation held for order " + orderId
                    );
                }
                targetQuantity = trackedQty;
            }

            int releaseQty = Math.min(targetQuantity, item.reservedQuantity());
            InventoryItem updated = new InventoryItem(
                    item.productId(),
                    item.name(),
                    item.availableQuantity() + releaseQty,
                    item.reservedQuantity() - releaseQty
            );
            inventoryRepository.save(updated);

            log.info("Compensated/Released {} units of {}. Restored available: {}",
                    releaseQty, productId, updated.availableQuantity());
            return new StockOperationResult(
                    true,
                    productId,
                    targetQuantity,
                    releaseQty,
                    item.availableQuantity(),
                    updated.availableQuantity(),
                    item.reservedQuantity(),
                    updated.reservedQuantity(),
                    "RELEASED"
            );
        } finally {
            distributedLock.release(lockToken);
        }
    }

    /**
     * Finalizes (commits) all active stock reservations held for a confirmed order.
     * Decrements reservedQuantity while keeping availableQuantity permanently reduced,
     * so reserved stock only reflects in-flight pending orders.
     */
    public List<StockOperationResult> commitForOrder(String orderId) {
        List<StockOperationResult> results = new ArrayList<>();
        if (orderId == null || orderId.isBlank()) {
            return results;
        }

        String prefix = orderId + ":";
        List<String> matchingKeys = activeOrderReservations.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .toList();

        for (String key : matchingKeys) {
            String productId = key.substring(prefix.length());
            String lockKey = "lock:stock:" + productId;
            LockToken lockToken = distributedLock.acquire(lockKey, Duration.ofSeconds(5));

            if (!lockToken.acquired()) {
                log.error("Could not acquire lock for product {} during stock commit for order {}.", productId, orderId);
                continue;
            }

            try {
                Integer trackedQty = activeOrderReservations.remove(key);
                if (trackedQty == null || trackedQty <= 0) {
                    continue;
                }

                Optional<InventoryItem> itemOpt = inventoryRepository.findById(productId);
                if (itemOpt.isEmpty()) {
                    log.warn("Product {} not found in inventory catalog during stock commit for order {}", productId, orderId);
                    continue;
                }

                InventoryItem item = itemOpt.get();
                int commitQty = Math.min(trackedQty, item.reservedQuantity());
                InventoryItem updated = new InventoryItem(
                        item.productId(),
                        item.name(),
                        item.availableQuantity(),
                        item.reservedQuantity() - commitQty
                );
                inventoryRepository.save(updated);

                log.info("Committed/Dispatched {} reserved unit(s) of {} for order {}. Avail={}, Remaining Reserved={}",
                        commitQty, productId, orderId, updated.availableQuantity(), updated.reservedQuantity());

                results.add(new StockOperationResult(
                        true,
                        productId,
                        trackedQty,
                        commitQty,
                        item.availableQuantity(),
                        updated.availableQuantity(),
                        item.reservedQuantity(),
                        updated.reservedQuantity(),
                        "COMMITTED"
                ));
            } finally {
                distributedLock.release(lockToken);
            }
        }

        return results;
    }

    private String allocationKey(String orderId, String productId) {
        return orderId + ":" + productId;
    }

    public void resetAll() {
        activeOrderReservations.clear();
        inventoryRepository.resetToDefaults();
        log.info("Inventory catalog and active order reservations reset to initial defaults.");
    }

    /**
     * Java 21 Loom Virtual Threads for high-concurrency stock audit
     */
    public void runVirtualThreadStockAudit() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            inventoryRepository.findAll().keySet().forEach(productId ->
                    executor.submit(() -> {
                        log.info("[VirtualThread: {}] Running stock reconciliation audit for {}",
                                Thread.currentThread(), productId);
                        getItem(productId).ifPresent(item ->
                                log.debug("Audited {}: Avail={}, Reserved={}",
                                        productId, item.availableQuantity(), item.reservedQuantity()));
                    })
            );
        }
    }
}
