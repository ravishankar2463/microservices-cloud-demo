package com.ravi.payment.domain.model;

public record PaymentResult(
    boolean success,
    PaymentStatus status,
    String transactionReference,
    String failureReason
) {
    public static PaymentResult captured(String transactionReference) {
        return new PaymentResult(true, PaymentStatus.CAPTURED, transactionReference, null);
    }

    public static PaymentResult declined(String reason) {
        return new PaymentResult(false, PaymentStatus.DECLINED, null, reason);
    }

    public static PaymentResult failed(String reason) {
        return new PaymentResult(false, PaymentStatus.FAILED, null, reason);
    }
}
