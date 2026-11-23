package com.ravi.payment.domain.event;

import java.math.BigDecimal;

public record PaymentCapturedEvent(
    String eventType,
    String orderId,
    String customerId,
    BigDecimal amount,
    String currency,
    String transactionReference
) {
    public static final String EVENT_TYPE = "PAYMENT_CAPTURED";

    public PaymentCapturedEvent(
            String orderId,
            String customerId,
            BigDecimal amount,
            String currency,
            String transactionReference) {
        this(EVENT_TYPE, orderId, customerId, amount, currency, transactionReference);
    }
}
