package com.ravi.product.presentation.rest.request;

import com.ravi.product.domain.model.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateProductRequest(
    @NotBlank(message = "SKU must not be blank")
    String sku,

    @NotBlank(message = "Name must not be blank")
    String name,

    String description,

    @NotNull(message = "Category must not be null")
    Category category,

    @NotNull(message = "Price must not be null")
    @Positive(message = "Price must be strictly positive")
    BigDecimal price
) {}
