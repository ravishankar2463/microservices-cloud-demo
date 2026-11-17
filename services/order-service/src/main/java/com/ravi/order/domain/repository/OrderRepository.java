package com.ravi.order.domain.repository;

import com.ravi.order.domain.model.Order;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain Repository Port for Order aggregates.
 * Inversion of control: Defined in Domain, implemented in Infrastructure.
 */
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(UUID id);
}
