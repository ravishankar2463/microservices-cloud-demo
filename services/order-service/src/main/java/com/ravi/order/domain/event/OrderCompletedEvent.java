package com.ravi.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event published via Transactional Outbox when an Order is fulfilled/completed
 * after reserved inventory has been committed for dispatch.
 */
public record OrderCompletedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        String status,
        String eventType,
        Instant occurredAt
) implements OrderEvent {

    public static final String EVENT_TYPE = "ORDER_COMPLETED";

    public OrderCompletedEvent(UUID eventId, UUID orderId, String customerId, BigDecimal totalAmount, Instant occurredAt) {
        this(eventId, orderId, customerId, totalAmount, "COMPLETED", EVENT_TYPE, occurredAt);
    }
}
