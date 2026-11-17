package com.ravi.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event published via Transactional Outbox when an Order is confirmed
 * after payment capture (triggers downstream invoice generation & archival).
 */
public record OrderConfirmedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        String status,
        String eventType,
        Instant occurredAt
) implements OrderEvent {

    public static final String EVENT_TYPE = "ORDER_CONFIRMED";

    public OrderConfirmedEvent(UUID eventId, UUID orderId, String customerId, BigDecimal totalAmount, Instant occurredAt) {
        this(eventId, orderId, customerId, totalAmount, "CONFIRMED", EVENT_TYPE, occurredAt);
    }
}
