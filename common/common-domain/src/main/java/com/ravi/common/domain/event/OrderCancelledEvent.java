package com.ravi.common.domain.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID eventId,
        UUID orderId,
        String reason,
        Instant occurredAt
) implements OrderEvent {}
