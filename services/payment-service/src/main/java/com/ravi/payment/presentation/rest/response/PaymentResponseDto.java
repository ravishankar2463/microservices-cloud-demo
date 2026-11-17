package com.ravi.payment.presentation.rest.response;

import com.ravi.payment.domain.model.Payment;
import com.ravi.payment.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponseDto(
    Long id,
    String orderId,
    String customerId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String transactionReference,
    String failureReason,
    Instant createdAt
) {
    public static PaymentResponseDto fromDomain(Payment payment) {
        return new PaymentResponseDto(
            payment.getId(),
            payment.getOrderId(),
            payment.getCustomerId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus(),
            payment.getTransactionReference(),
            payment.getFailureReason(),
            payment.getCreatedAt()
        );
    }
}
