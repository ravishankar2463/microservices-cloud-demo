package com.ravi.product.presentation.rest.request;

import com.ravi.product.domain.model.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Request body to create a new product catalog entry")
public record CreateProductRequest(
    @Schema(description = "Unique alphanumeric stock-keeping unit", example = "TECH-KB-001")
    @NotBlank(message = "SKU must not be blank")
    String sku,

    @Schema(description = "Human-readable display name of the product", example = "Mechanical Gaming Keyboard")
    @NotBlank(message = "Name must not be blank")
    String name,

    @Schema(description = "Detailed catalog description", example = "Hot-swappable tactile RGB wireless mechanical keyboard with PBT keycaps")
    String description,

    @Schema(description = "Product category", example = "ELECTRONICS")
    @NotNull(message = "Category must not be null")
    Category category,

    @Schema(description = "Unit selling price in USD", example = "129.99")
    @NotNull(message = "Price must not be null")
    @Positive(message = "Price must be strictly positive")
    BigDecimal price
) {}
