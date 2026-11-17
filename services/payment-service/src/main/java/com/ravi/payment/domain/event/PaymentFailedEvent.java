package com.ravi.payment.domain.event;

import java.math.BigDecimal;

public record PaymentFailedEvent(
    String orderId,
    String customerId,
    BigDecimal amount,
    String failureReason
) {
}
