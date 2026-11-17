package com.ravi.payment.domain.event;

import java.math.BigDecimal;

public record PaymentCapturedEvent(
    String orderId,
    String customerId,
    BigDecimal amount,
    String currency,
    String transactionReference
) {
}
