package com.ravi.order.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pure Domain Aggregate Root for an Order.
 * Contains no Spring, JPA, or infrastructure dependencies.
 */
public class Order {

    private final UUID id;
    private final String customerId;
    private final List<OrderItem> items;
    private final BigDecimal totalAmount;
    private OrderStatus status;
    private final Instant createdAt;

    public Order(UUID id, String customerId, List<OrderItem> items) {
        if (id == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        this.id = id;
        this.customerId = customerId;
        this.items = List.copyOf(items);
        this.totalAmount = this.items.stream()
                .map(OrderItem::subTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public Order(UUID id, String customerId, List<OrderItem> items, BigDecimal totalAmount, OrderStatus status, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.items = List.copyOf(items);
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void cancel(String reason) {
        if (this.status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is already cancelled");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void markInventoryReserved() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can have inventory reserved");
        }
        this.status = OrderStatus.INVENTORY_RESERVED;
    }

    public void confirm() {
        if (this.status != OrderStatus.PENDING && this.status != OrderStatus.INVENTORY_RESERVED) {
            throw new IllegalStateException("Only pending or inventory-reserved orders can be confirmed");
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
