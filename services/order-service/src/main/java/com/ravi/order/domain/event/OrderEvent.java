package com.ravi.order.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all Order domain events.
 */
public interface OrderEvent {
    UUID eventId();
    UUID orderId();
    String eventType();
    Instant occurredAt();
}
