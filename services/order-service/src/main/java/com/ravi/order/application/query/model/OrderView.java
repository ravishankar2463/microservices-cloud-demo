package com.ravi.order.application.query.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CQRS Read-Model projection for Order queries.
 */
@Schema(description = "CQRS Read-model projection representing full aggregated order details")
public record OrderView(
        @Schema(description = "Unique UUID of the order", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID orderId,

        @Schema(description = "Customer ID associated with the order", example = "cust_84920")
        String customerId,

        @Schema(description = "Calculated total monetary value of the order", example = "99.98")
        BigDecimal totalAmount,

        @Schema(description = "Current lifecycle status of the order (PENDING, PAID, COMPLETED, CANCELLED)", example = "COMPLETED")
        String status,

        @Schema(description = "Timestamp when the order was initially placed", example = "2026-11-23T15:30:00Z")
        Instant createdAt,

        @Schema(description = "List of items in the order")
        List<ItemView> items
) {
    @Schema(description = "Read-model line item projection")
    public record ItemView(
            @Schema(description = "Product SKU / ID", example = "PROD-TECH-001")
            String productId,

            @Schema(description = "Purchased quantity", example = "2")
            int quantity,

            @Schema(description = "Unit price charged", example = "49.99")
            BigDecimal unitPrice
    ) {}
}
