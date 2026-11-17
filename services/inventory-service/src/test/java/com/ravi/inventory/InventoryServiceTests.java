package com.ravi.inventory;

import com.ravi.inventory.application.service.InventoryReservationService;
import com.ravi.inventory.domain.model.InventoryItem;
import com.ravi.inventory.infrastructure.redis.RedisDistributedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InventoryServiceTests {

    @Autowired
    private InventoryReservationService reservationService;

    @Autowired
    private RedisDistributedLock distributedLock;

    @Test
    @DisplayName("Inventory: Read stock from catalog")
    void testGetStock() {
        Optional<InventoryItem> itemOpt = reservationService.getItem("PROD-LAPTOP");
        assertThat(itemOpt).isPresent();
        assertThat(itemOpt.get().name()).isEqualTo("Dev Laptop Pro");
        assertThat(itemOpt.get().availableQuantity()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Inventory: Reserve stock safely with Redis Distributed Lock")
    void testReserveStock() {
        int initialQty = reservationService.getItem("PROD-MOUSE").orElseThrow().availableQuantity();

        boolean reserved = reservationService.reserveStock("PROD-MOUSE", 5);
        assertThat(reserved).isTrue();

        int remainingQty = reservationService.getItem("PROD-MOUSE").orElseThrow().availableQuantity();
        assertThat(remainingQty).isEqualTo(initialQty - 5);
    }

    @Test
    @DisplayName("Inventory: Reject reservation when requested quantity exceeds available stock")
    void testReserveExcessiveStock() {
        boolean reserved = reservationService.reserveStock("PROD-KEYBOARD", 9999);
        assertThat(reserved).isFalse();
    }

    @Test
    @DisplayName("Inventory: Java 21 Virtual Threads stock audit runs without exceptions")
    void testVirtualThreadStockAudit() {
        reservationService.runVirtualThreadStockAudit();
    }

    @Test
    @DisplayName("Inventory: Saga compensating transaction restores reserved stock")
    void testCompensatingStockRelease() {
        String productId = "PROD-KEYBOARD";
        int initialAvailable = reservationService.getItem(productId).orElseThrow().availableQuantity();

        // 1. Reserve 10 units
        boolean reserved = reservationService.reserveStock(productId, 10);
        assertThat(reserved).isTrue();
        assertThat(reservationService.getItem(productId).orElseThrow().availableQuantity())
                .isEqualTo(initialAvailable - 10);
        assertThat(reservationService.getItem(productId).orElseThrow().reservedQuantity())
                .isEqualTo(10);

        // 2. Compensate / Release 10 units
        boolean released = reservationService.releaseStock(productId, 10);
        assertThat(released).isTrue();
        assertThat(reservationService.getItem(productId).orElseThrow().availableQuantity())
                .isEqualTo(initialAvailable);
        assertThat(reservationService.getItem(productId).orElseThrow().reservedQuantity())
                .isEqualTo(0);
    }
}
