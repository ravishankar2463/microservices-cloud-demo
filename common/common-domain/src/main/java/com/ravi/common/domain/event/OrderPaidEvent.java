package com.ravi.common.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPaidEvent(
        UUID eventId,
        UUID orderId,
        String paymentReference,
        BigDecimal amountPaid,
        Instant occurredAt
) implements OrderEvent {}
