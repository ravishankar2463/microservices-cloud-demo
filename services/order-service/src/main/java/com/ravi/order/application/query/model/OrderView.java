package com.ravi.order.application.query.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CQRS Read-Model projection for Order queries.
 */
public record OrderView(
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        String status,
        Instant createdAt,
        List<ItemView> items
) {
    public record ItemView(
            String productId,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
