package com.ravi.common.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        List<OrderItemPayload> items,
        BigDecimal totalAmount,
        Instant occurredAt
) implements OrderEvent {

    public record OrderItemPayload(String productId, int quantity, BigDecimal unitPrice) {}
}
