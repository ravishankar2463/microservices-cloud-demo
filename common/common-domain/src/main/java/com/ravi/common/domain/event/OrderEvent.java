package com.ravi.common.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Algebraic Data Type (ADT) hierarchy representing Domain Events.
 * Leverages Java 21 Sealed Interfaces, Records, and Pattern Matching.
 */
public sealed interface OrderEvent permits OrderCreatedEvent, OrderPaidEvent, OrderCancelledEvent {

    UUID eventId();

    UUID orderId();

    Instant occurredAt();

    default String eventType() {
        return switch (this) {
            case OrderCreatedEvent event -> "ORDER_CREATED";
            case OrderPaidEvent event -> "ORDER_PAID";
            case OrderCancelledEvent event -> "ORDER_CANCELLED";
        };
    }
}
