package com.ravi.product.presentation.rest.response;

import com.ravi.product.domain.model.Category;
import com.ravi.product.domain.model.Product;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductDto(
    Long id,
    String sku,
    String name,
    String description,
    Category category,
    BigDecimal price,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static ProductDto fromEntity(Product p) {
        return new ProductDto(
            p.getId(),
            p.getSku(),
            p.getName(),
            p.getDescription(),
            p.getCategory(),
            p.getPrice(),
            p.isActive(),
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}
