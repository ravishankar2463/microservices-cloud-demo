package com.ravi.payment.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ProcessPaymentCommand(
    @NotBlank(message = "Order ID is required")
    String orderId,

    @NotBlank(message = "Customer ID is required")
    String customerId,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be strictly positive")
    BigDecimal amount,

    String currency,

    @NotBlank(message = "Idempotency key is required")
    String idempotencyKey,

    String paymentMethod
) {
}
