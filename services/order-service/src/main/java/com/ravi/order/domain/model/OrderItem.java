package com.ravi.order.domain.model;

import java.math.BigDecimal;

/**
 * Value Object representing an individual line item in an Order.
 */
public record OrderItem(
        String productId,
        int quantity,
        BigDecimal unitPrice
) {
    public OrderItem {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID cannot be null or blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unit price must be greater than zero");
        }
    }

    public BigDecimal subTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
