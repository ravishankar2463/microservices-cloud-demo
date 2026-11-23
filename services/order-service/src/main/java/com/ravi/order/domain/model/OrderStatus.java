package com.ravi.order.domain.model;

/**
 * Domain Enum capturing the lifecycle states of an Order.
 */
public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    CONFIRMED,
    COMPLETED,
    PAYMENT_FAILED,
    CANCELLED
}
