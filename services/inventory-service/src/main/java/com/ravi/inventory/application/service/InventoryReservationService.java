package com.ravi.inventory.application.service;

import com.ravi.inventory.application.port.out.DistributedLockPort;
import com.ravi.inventory.application.port.out.DistributedLockPort.LockToken;
import com.ravi.inventory.domain.model.InventoryItem;
import com.ravi.inventory.domain.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;

@Service
public class InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);

    private final InventoryRepository inventoryRepository;
    private final DistributedLockPort distributedLock;

    public InventoryReservationService(InventoryRepository inventoryRepository, DistributedLockPort distributedLock) {
        this.inventoryRepository = inventoryRepository;
        this.distributedLock = distributedLock;
    }

    public Optional<InventoryItem> getItem(String productId) {
        return inventoryRepository.findById(productId);
    }

    public boolean reserveStock(String productId, int quantity) {
        String lockKey = "lock:stock:" + productId;
        LockToken lockToken = distributedLock.acquire(lockKey, Duration.ofSeconds(5));

        if (!lockToken.acquired()) {
            log.error("Could not acquire lock for product {}. Stock reservation aborted due to contention.", productId);
            return false;
        }

        try {
            Optional<InventoryItem> itemOpt = inventoryRepository.findById(productId);
            if (itemOpt.isEmpty()) {
                log.warn("Product {} not found in inventory catalog", productId);
                return false;
            }

            InventoryItem item = itemOpt.get();
            if (item.availableQuantity() < quantity) {
                log.warn("Insufficient stock for product {}. Available: {}, Requested: {}",
                        productId, item.availableQuantity(), quantity);
                return false;
            }

            // Perform atomic stock reduction
            InventoryItem updated = new InventoryItem(
                    item.productId(),
                    item.name(),
                    item.availableQuantity() - quantity,
                    item.reservedQuantity() + quantity
            );
            inventoryRepository.save(updated);

            log.info("Reserved {} units of {}. Remaining available: {}",
                    quantity, productId, updated.availableQuantity());
            return true;
        } finally {
            distributedLock.release(lockToken);
        }
    }

    /**
     * Compensating transaction for Saga orchestration:
     * Releases previously reserved stock back to available stock.
     */
    public boolean releaseStock(String productId, int quantity) {
        String lockKey = "lock:stock:" + productId;
        LockToken lockToken = distributedLock.acquire(lockKey, Duration.ofSeconds(5));

        if (!lockToken.acquired()) {
            log.error("Could not acquire lock for product {}. Stock compensation release aborted due to contention.", productId);
            return false;
        }

        try {
            Optional<InventoryItem> itemOpt = inventoryRepository.findById(productId);
            if (itemOpt.isEmpty()) {
                log.warn("Product {} not found in inventory catalog during compensation release", productId);
                return false;
            }

            InventoryItem item = itemOpt.get();
            int releaseQty = Math.min(quantity, item.reservedQuantity());
            InventoryItem updated = new InventoryItem(
                    item.productId(),
                    item.name(),
                    item.availableQuantity() + releaseQty,
                    item.reservedQuantity() - releaseQty
            );
            inventoryRepository.save(updated);

            log.info("Compensated/Released {} units of {}. Restored available: {}",
                    releaseQty, productId, updated.availableQuantity());
            return true;
        } finally {
            distributedLock.release(lockToken);
        }
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
