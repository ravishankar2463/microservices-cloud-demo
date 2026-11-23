package com.ravi.order.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * Command representing customer intent to create a new order.
 */
@Schema(description = "Command payload to place a new customer order")
public record CreateOrderCommand(
        @Schema(description = "Unique identifier of the customer placing the order", example = "cust_84920")
        @NotBlank(message = "Customer ID is required")
        String customerId,

        @Schema(description = "Collection of line items included in the order")
        @NotEmpty(message = "Order must have at least one item")
        List<ItemCommand> items
) {
    @Schema(description = "Individual line item in the order")
    public record ItemCommand(
            @Schema(description = "Unique product SKU / product ID", example = "PROD-TECH-001")
            @NotBlank(message = "Product ID is required")
            String productId,

            @Schema(description = "Quantity ordered (must be positive)", example = "2", minimum = "1")
            @Positive(message = "Quantity must be positive")
            int quantity,

            @Schema(description = "Unit price of the item at purchase time", example = "49.99")
            @Positive(message = "Unit price must be positive")
            BigDecimal unitPrice
    ) {}
}
