package com.ravi.payment.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Command payload to process an order payment")
public record ProcessPaymentCommand(
    @Schema(description = "UUID string of the target order", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotBlank(message = "Order ID is required")
    String orderId,

    @Schema(description = "Identifier of the customer making the payment", example = "cust_84920")
    @NotBlank(message = "Customer ID is required")
    String customerId,

    @Schema(description = "Payment amount (values ending in .99 trigger simulated DECLINE; .50 trigger TIMEOUT)", example = "99.98")
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be strictly positive")
    BigDecimal amount,

    @Schema(description = "ISO 4217 Currency code", example = "USD")
    String currency,

    @Schema(description = "Unique idempotency token preventing duplicate charges", example = "idemp_order_3fa85f64")
    @NotBlank(message = "Idempotency key is required")
    String idempotencyKey,

    @Schema(description = "Payment instrument method (e.g. CREDIT_CARD, DEBIT_CARD, PAYPAL)", example = "CREDIT_CARD")
    String paymentMethod
) {
}
