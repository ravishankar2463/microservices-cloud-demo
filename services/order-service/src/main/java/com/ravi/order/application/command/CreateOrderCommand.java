package com.ravi.order.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * Command representing customer intent to create a new order.
 */
public record CreateOrderCommand(
        @NotBlank(message = "Customer ID is required")
        String customerId,

        @NotEmpty(message = "Order must have at least one item")
        List<ItemCommand> items
) {
    public record ItemCommand(
            @NotBlank(message = "Product ID is required")
            String productId,

            @Positive(message = "Quantity must be positive")
            int quantity,

            @Positive(message = "Unit price must be positive")
            BigDecimal unitPrice
    ) {}
}
