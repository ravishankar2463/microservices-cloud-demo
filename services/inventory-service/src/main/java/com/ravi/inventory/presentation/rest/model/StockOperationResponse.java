package com.ravi.inventory.presentation.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Outcome response for stock reservation or compensation")
public record StockOperationResponse(
        @Schema(description = "Whether the stock operation succeeded", example = "true")
        boolean success,

        @Schema(description = "Status or confirmation message", example = "Stock reserved successfully")
        String message,

        @Schema(description = "Target product ID", example = "PROD-TECH-001")
        String productId,

        @Schema(description = "Quantity reserved or released", example = "2")
        int quantity
) {}
