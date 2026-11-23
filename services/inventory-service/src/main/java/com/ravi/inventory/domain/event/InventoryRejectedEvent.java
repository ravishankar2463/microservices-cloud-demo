package com.ravi.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryRejectedEvent(
        UUID eventId,
        String eventType,
        String orderId,
        String customerId,
        String productId,
        int requestedQuantity,
        int availableQuantity,
        int reservedQuantity,
        String reason,
        Instant occurredAt
) {
    public InventoryRejectedEvent(
            UUID eventId,
            String orderId,
            String customerId,
            String productId,
            int requestedQuantity,
            int availableQuantity,
            int reservedQuantity,
            String reason,
            Instant occurredAt) {
        this(eventId, "INVENTORY_REJECTED", orderId, customerId, productId, requestedQuantity,
                availableQuantity, reservedQuantity, reason, occurredAt);
    }
}
