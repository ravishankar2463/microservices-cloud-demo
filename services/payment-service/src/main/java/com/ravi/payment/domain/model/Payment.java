package com.ravi.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class Payment {

    private Long id;
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String idempotencyKey;
    private String paymentMethod;
    private String transactionReference;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public Payment() {
    }

    public Payment(String orderId, String customerId, BigDecimal amount, String currency, String idempotencyKey, String paymentMethod) {
        this.orderId = Objects.requireNonNull(orderId, "Order ID must not be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID must not be null");
        this.amount = Objects.requireNonNull(amount, "Amount must not be null");
        this.currency = currency != null ? currency : "USD";
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "Idempotency key must not be null");
        this.paymentMethod = paymentMethod != null ? paymentMethod : "CREDIT_CARD";
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Payment(Long id, String orderId, String customerId, BigDecimal amount, String currency,
                   PaymentStatus status, String idempotencyKey, String paymentMethod,
                   String transactionReference, String failureReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void markCaptured(String transactionReference) {
        this.status = PaymentStatus.CAPTURED;
        this.transactionReference = transactionReference;
        this.updatedAt = Instant.now();
    }

    public void markDeclined(String reason) {
        this.status = PaymentStatus.DECLINED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
