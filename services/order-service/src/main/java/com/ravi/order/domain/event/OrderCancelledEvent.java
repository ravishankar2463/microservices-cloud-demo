package com.ravi.order.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event published when an Order is cancelled (triggers Saga compensation).
 */
public record OrderCancelledEvent(
        UUID eventId,
        UUID orderId,
        String reason,
        List<OrderCreatedEvent.ItemPayload> items,
        Instant occurredAt
) implements OrderEvent {

    public static final String EVENT_TYPE = "ORDER_CANCELLED";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
