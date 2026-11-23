package com.ravi.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryCommittedEvent(
        UUID eventId,
        String eventType,
        String orderId,
        String productId,
        int committedQuantity,
        int stockBefore,
        int stockAfter,
        int reservedBefore,
        int reservedAfter,
        Instant occurredAt
) {
    public InventoryCommittedEvent(
            UUID eventId,
            String orderId,
            String productId,
            int committedQuantity,
            int stockBefore,
            int stockAfter,
            int reservedBefore,
            int reservedAfter,
            Instant occurredAt) {
        this(eventId, "INVENTORY_COMMITTED", orderId, productId, committedQuantity,
                stockBefore, stockAfter, reservedBefore, reservedAfter, occurredAt);
    }
}
