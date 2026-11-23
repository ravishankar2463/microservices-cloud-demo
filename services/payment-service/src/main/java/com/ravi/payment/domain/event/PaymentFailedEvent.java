package com.ravi.payment.domain.event;

import java.math.BigDecimal;

public record PaymentFailedEvent(
    String eventType,
    String orderId,
    String customerId,
    BigDecimal amount,
    String failureReason
) {
    public static final String EVENT_TYPE = "PAYMENT_FAILED";

    public PaymentFailedEvent(
            String orderId,
            String customerId,
            BigDecimal amount,
            String failureReason) {
        this(EVENT_TYPE, orderId, customerId, amount, failureReason);
    }
}
