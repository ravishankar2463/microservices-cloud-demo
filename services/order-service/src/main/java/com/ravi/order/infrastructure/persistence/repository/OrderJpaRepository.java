package com.ravi.order.infrastructure.persistence.repository;

import com.ravi.order.domain.model.OrderStatus;
import com.ravi.order.infrastructure.persistence.entity.OrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for OrderJpaEntity.
 */
@Repository
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {
    List<OrderJpaEntity> findByCustomerId(String customerId);
    List<OrderJpaEntity> findByStatus(OrderStatus status);
}
