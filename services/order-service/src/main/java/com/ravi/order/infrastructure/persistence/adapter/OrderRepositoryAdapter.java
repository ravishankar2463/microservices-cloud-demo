package com.ravi.order.infrastructure.persistence.adapter;

import com.ravi.order.domain.model.Order;
import com.ravi.order.domain.model.OrderItem;
import com.ravi.order.domain.repository.OrderRepository;
import com.ravi.order.infrastructure.persistence.entity.OrderItemJpaEntity;
import com.ravi.order.infrastructure.persistence.entity.OrderJpaEntity;
import com.ravi.order.infrastructure.persistence.repository.OrderJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Technical persistence adapter implementing the Domain OrderRepository port.
 */
@Component
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
        for (OrderItem item : order.getItems()) {
            entity.addItem(new OrderItemJpaEntity(item.productId(), item.quantity(), item.unitPrice()));
        }
        OrderJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(i -> new OrderItem(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new Order(
                entity.getId(),
                entity.getCustomerId(),
                items,
                entity.getTotalAmount(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
