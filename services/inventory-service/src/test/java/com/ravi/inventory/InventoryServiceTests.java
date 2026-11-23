package com.ravi.inventory;

import com.ravi.inventory.application.service.InventoryReservationService;
import com.ravi.inventory.domain.event.InventoryCommittedEvent;
import com.ravi.inventory.domain.event.InventoryRejectedEvent;
import com.ravi.inventory.domain.event.InventoryReleasedEvent;
import com.ravi.inventory.domain.event.InventoryReservedEvent;
import com.ravi.inventory.domain.model.InventoryItem;
import com.ravi.inventory.infrastructure.messaging.consumer.OrderEventListener;
import com.ravi.inventory.infrastructure.messaging.producer.InventoryEventPublisher;
import com.ravi.inventory.infrastructure.redis.RedisDistributedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Test
    @DisplayName("Inventory Listener: Emits InventoryRejectedEvent on exhaustion and ignores subsequent cancel compensation")
    void testInventoryExhaustionEmitsRejectedAndDoesNotStealOtherOrderStock() {
        List<InventoryReservedEvent> reservedEvents = new ArrayList<>();
        List<InventoryRejectedEvent> rejectedEvents = new ArrayList<>();
        List<InventoryReleasedEvent> releasedEvents = new ArrayList<>();

        InventoryEventPublisher recordingPublisher = new InventoryEventPublisher(null, new ObjectMapper()) {
            @Override
            public void publishInventoryReserved(InventoryReservedEvent event) {
                reservedEvents.add(event);
            }

            @Override
            public void publishInventoryRejected(InventoryRejectedEvent event) {
                rejectedEvents.add(event);
            }

            @Override
            public void publishInventoryReleased(InventoryReleasedEvent event) {
                releasedEvents.add(event);
            }
        };

        OrderEventListener listener = new OrderEventListener(reservationService, recordingPublisher, new ObjectMapper());
        String productId = "PROD-TECH-001";
        int initialAvailable = reservationService.getItem(productId).orElseThrow().availableQuantity();
        int initialReserved = reservationService.getItem(productId).orElseThrow().reservedQuantity();

        // 1. Place a valid 1-unit order first so reservedQuantity > 0
        String validOrderId = UUID.randomUUID().toString();
        listener.onOrderEvent("""
                {"eventType":"ORDER_CREATED","orderId":"%s","customerId":"cust-valid","items":[{"productId":"%s","quantity":1,"unitPrice":149.00}]}
                """.formatted(validOrderId, productId));

        assertThat(reservedEvents).hasSize(1);
        assertThat(reservedEvents.getFirst().stockBefore()).isEqualTo(initialAvailable);
        assertThat(reservedEvents.getFirst().stockAfter()).isEqualTo(initialAvailable - 1);

        // 2. Place an overstock 999-unit order -> must reject without mutating stock
        String overstockOrderId = UUID.randomUUID().toString();
        listener.onOrderEvent("""
                {"eventType":"ORDER_CREATED","orderId":"%s","customerId":"customer-overstock","items":[{"productId":"%s","quantity":999,"unitPrice":149.00}]}
                """.formatted(overstockOrderId, productId));

        assertThat(rejectedEvents).hasSize(1);
        assertThat(rejectedEvents.getFirst().orderId()).isEqualTo(overstockOrderId);
        assertThat(rejectedEvents.getFirst().requestedQuantity()).isEqualTo(999);
        assertThat(rejectedEvents.getFirst().availableQuantity()).isEqualTo(initialAvailable - 1);
        assertThat(rejectedEvents.getFirst().reason()).contains("Insufficient stock");

        // 3. When Saga cancels the rejected order, it MUST NOT release the valid order's reserved stock
        listener.onOrderEvent("""
                {"eventType":"ORDER_CANCELLED","orderId":"%s","reason":"Inventory rejected","items":[{"productId":"%s","quantity":999,"unitPrice":149.00}]}
                """.formatted(overstockOrderId, productId));

        assertThat(releasedEvents).isEmpty();
        assertThat(reservationService.getItem(productId).orElseThrow().availableQuantity()).isEqualTo(initialAvailable - 1);
        assertThat(reservationService.getItem(productId).orElseThrow().reservedQuantity()).isEqualTo(initialReserved + 1);

        // 4. Cancelling the valid order releases its 1 reserved unit and emits InventoryReleasedEvent
        listener.onOrderEvent("""
                {"eventType":"ORDER_CANCELLED","orderId":"%s","reason":"Customer cancelled","items":[{"productId":"%s","quantity":1,"unitPrice":149.00}]}
                """.formatted(validOrderId, productId));

        assertThat(releasedEvents).hasSize(1);
        assertThat(releasedEvents.getFirst().orderId()).isEqualTo(validOrderId);
        assertThat(releasedEvents.getFirst().releasedQuantity()).isEqualTo(1);
        assertThat(reservationService.getItem(productId).orElseThrow().availableQuantity()).isEqualTo(initialAvailable);
        assertThat(reservationService.getItem(productId).orElseThrow().reservedQuantity()).isEqualTo(initialReserved);
    }

    @Test
    @DisplayName("Inventory Listener: Commits reserved stock on ORDER_CONFIRMED and ignores ORDER_COMPLETED")
    void testOrderConfirmedCommitsReservedStockAndClearsHold() {
        List<InventoryReservedEvent> reservedEvents = new ArrayList<>();
        List<InventoryCommittedEvent> committedEvents = new ArrayList<>();

        InventoryEventPublisher recordingPublisher = new InventoryEventPublisher(null, new ObjectMapper()) {
            @Override
            public void publishInventoryReserved(InventoryReservedEvent event) {
                reservedEvents.add(event);
            }

            @Override
            public void publishInventoryCommitted(InventoryCommittedEvent event) {
                committedEvents.add(event);
            }
        };

        OrderEventListener listener = new OrderEventListener(reservationService, recordingPublisher, new ObjectMapper());
        String productId = "PROD-LAPTOP";
        int initialAvailable = reservationService.getItem(productId).orElseThrow().availableQuantity();
        int initialReserved = reservationService.getItem(productId).orElseThrow().reservedQuantity();
        String orderId = UUID.randomUUID().toString();

        // 1. ORDER_CREATED -> soft hold: avail - 1, reserved + 1
        listener.onOrderEvent("""
                {"eventType":"ORDER_CREATED","orderId":"%s","customerId":"cust-commit","items":[{"productId":"%s","quantity":1,"unitPrice":1200.00}]}
                """.formatted(orderId, productId));
        assertThat(reservedEvents).hasSize(1);
        assertThat(reservationService.getItem(productId).orElseThrow().availableQuantity()).isEqualTo(initialAvailable - 1);
        assertThat(reservationService.getItem(productId).orElseThrow().reservedQuantity()).isEqualTo(initialReserved + 1);

        // 2. ORDER_CONFIRMED -> commit hold: avail stays -1 (sold), reserved returns to initial
        listener.onOrderEvent("""
                {"eventType":"ORDER_CONFIRMED","orderId":"%s","customerId":"cust-commit","status":"CONFIRMED","totalAmount":1200.00}
                """.formatted(orderId));
        assertThat(committedEvents).hasSize(1);
        assertThat(committedEvents.getFirst().committedQuantity()).isEqualTo(1);
        assertThat(committedEvents.getFirst().stockBefore()).isEqualTo(initialAvailable - 1);
        assertThat(committedEvents.getFirst().stockAfter()).isEqualTo(initialAvailable - 1);
        assertThat(committedEvents.getFirst().reservedBefore()).isEqualTo(initialReserved + 1);
        assertThat(committedEvents.getFirst().reservedAfter()).isEqualTo(initialReserved);
        assertThat(reservationService.getItem(productId).orElseThrow().availableQuantity()).isEqualTo(initialAvailable - 1);
        assertThat(reservationService.getItem(productId).orElseThrow().reservedQuantity()).isEqualTo(initialReserved);

        // 3. ORDER_COMPLETED -> safe no-op, does not re-reserve stock
        listener.onOrderEvent("""
                {"eventType":"ORDER_COMPLETED","orderId":"%s","customerId":"cust-commit","status":"COMPLETED","totalAmount":1200.00}
                """.formatted(orderId));
        assertThat(reservedEvents).hasSize(1);
        assertThat(committedEvents).hasSize(1);
        assertThat(reservationService.getItem(productId).orElseThrow().availableQuantity()).isEqualTo(initialAvailable - 1);
        assertThat(reservationService.getItem(productId).orElseThrow().reservedQuantity()).isEqualTo(initialReserved);
    }
}
