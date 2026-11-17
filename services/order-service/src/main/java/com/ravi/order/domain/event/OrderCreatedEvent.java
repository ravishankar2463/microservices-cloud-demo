package com.ravi.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event published when a new Order is placed.
 */
public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        List<ItemPayload> items,
        BigDecimal totalAmount,
        Instant occurredAt
) implements OrderEvent {

    public static final String EVENT_TYPE = "ORDER_CREATED";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    public record ItemPayload(
            String productId,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
