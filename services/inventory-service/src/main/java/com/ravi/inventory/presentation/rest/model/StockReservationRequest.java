package com.ravi.inventory.presentation.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "Payload to request stock reservation or release")
public record StockReservationRequest(
        @Schema(description = "Product SKU or ID to reserve/release", example = "PROD-TECH-001")
        @NotBlank(message = "Product ID is required")
        String productId,

        @Schema(description = "Quantity of units (must be positive)", example = "2", minimum = "1")
        @Positive(message = "Quantity must be positive")
        int quantity
) {}
