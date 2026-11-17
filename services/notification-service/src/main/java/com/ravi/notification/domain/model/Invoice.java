package com.ravi.notification.domain.model;

import java.time.Instant;

public record Invoice(
        String orderId,
        String customerId,
        String payload,
        String status,
        Instant createdAt
) {
    public static Invoice create(String orderId, String customerId, String payload) {
        return new Invoice(orderId, customerId, payload, "GENERATED", Instant.now());
    }
}
