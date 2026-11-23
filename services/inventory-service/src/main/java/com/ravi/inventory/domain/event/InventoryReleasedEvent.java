package com.ravi.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReleasedEvent(
        UUID eventId,
        String eventType,
        String orderId,
        String productId,
        int releasedQuantity,
        int stockBefore,
        int stockAfter,
        int reservedBefore,
        int reservedAfter,
        Instant occurredAt
) {
    public InventoryReleasedEvent(
            UUID eventId,
            String orderId,
            String productId,
            int releasedQuantity,
            int stockBefore,
            int stockAfter,
            int reservedBefore,
            int reservedAfter,
            Instant occurredAt) {
        this(eventId, "INVENTORY_RELEASED", orderId, productId, releasedQuantity,
                stockBefore, stockAfter, reservedBefore, reservedAfter, occurredAt);
    }
}
