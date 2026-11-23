package com.ravi.product.presentation.rest.response;

import com.ravi.product.domain.model.Category;
import com.ravi.product.domain.model.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Product details DTO returned from catalog queries")
public record ProductDto(
    @Schema(description = "Unique auto-generated internal database ID", example = "1")
    Long id,

    @Schema(description = "Unique SKU", example = "TECH-KB-001")
    String sku,

    @Schema(description = "Display name", example = "Mechanical Gaming Keyboard")
    String name,

    @Schema(description = "Catalog description", example = "Hot-swappable tactile RGB wireless mechanical keyboard with PBT keycaps")
    String description,

    @Schema(description = "Category", example = "ELECTRONICS")
    Category category,

    @Schema(description = "Price in USD", example = "129.99")
    BigDecimal price,

    @Schema(description = "Whether product is active and purchasable", example = "true")
    boolean active,

    @Schema(description = "Creation timestamp", example = "2026-11-23T12:00:00Z")
    Instant createdAt,

    @Schema(description = "Last update timestamp", example = "2026-11-23T14:30:00Z")
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
