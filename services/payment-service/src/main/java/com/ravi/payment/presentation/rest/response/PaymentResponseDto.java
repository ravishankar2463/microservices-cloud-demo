package com.ravi.payment.presentation.rest.response;

import com.ravi.payment.domain.model.Payment;
import com.ravi.payment.domain.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Payment transaction details and status")
public record PaymentResponseDto(
    @Schema(description = "Internal payment record ID", example = "1")
    Long id,

    @Schema(description = "Associated order UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    String orderId,

    @Schema(description = "Customer ID", example = "cust_84920")
    String customerId,

    @Schema(description = "Amount charged", example = "99.98")
    BigDecimal amount,

    @Schema(description = "Currency code", example = "USD")
    String currency,

    @Schema(description = "Transaction settlement status (PENDING, CAPTURED, DECLINED, REFUNDED, FAILED)", example = "CAPTURED")
    PaymentStatus status,

    @Schema(description = "External gateway transaction reference token", example = "TXN_7749219384")
    String transactionReference,

    @Schema(description = "Reason message if declined or failed", example = "None")
    String failureReason,

    @Schema(description = "Timestamp when payment record was created", example = "2026-11-23T15:30:05Z")
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
