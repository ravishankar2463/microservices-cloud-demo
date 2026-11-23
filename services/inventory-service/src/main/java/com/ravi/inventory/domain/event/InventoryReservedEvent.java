package com.ravi.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedEvent(
        UUID eventId,
        String eventType,
        String orderId,
        String customerId,
        String productId,
        int requestedQuantity,
        int stockBefore,
        int stockAfter,
        int reservedBefore,
        int reservedAfter,
        Instant occurredAt
) {
    public InventoryReservedEvent(
            UUID eventId,
            String orderId,
            String customerId,
            String productId,
            int requestedQuantity,
            int stockBefore,
            int stockAfter,
            int reservedBefore,
            int reservedAfter,
            Instant occurredAt) {
        this(eventId, "INVENTORY_RESERVED", orderId, customerId, productId, requestedQuantity,
                stockBefore, stockAfter, reservedBefore, reservedAfter, occurredAt);
    }
}
