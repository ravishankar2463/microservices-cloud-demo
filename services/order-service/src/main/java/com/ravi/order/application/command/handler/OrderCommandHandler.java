package com.ravi.order.application.command.handler;

import com.ravi.order.application.command.CancelOrderCommand;
import com.ravi.order.application.command.CreateOrderCommand;
import com.ravi.order.domain.event.OrderCancelledEvent;
import com.ravi.order.domain.event.OrderCompletedEvent;
import com.ravi.order.domain.event.OrderConfirmedEvent;
import com.ravi.order.domain.event.OrderCreatedEvent;
import com.ravi.order.domain.model.Order;
import com.ravi.order.domain.model.OrderItem;
import com.ravi.order.domain.model.OrderStatus;
import com.ravi.order.infrastructure.messaging.outbox.OutboxJpaEntity;
import com.ravi.order.infrastructure.messaging.outbox.OutboxJpaRepository;
import com.ravi.order.infrastructure.persistence.entity.OrderItemJpaEntity;
import com.ravi.order.infrastructure.persistence.entity.OrderJpaEntity;
import com.ravi.order.infrastructure.persistence.repository.OrderJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application Command Handler coordinating write-side use-cases.
 * Orchestrates pure domain rules, writes state to database, and writes to transactional outbox.
 */
@Service
public class OrderCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandHandler.class);

    private final OrderJpaRepository orderRepository;
    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public OrderCommandHandler(OrderJpaRepository orderRepository,
                               OutboxJpaRepository outboxRepository,
                               ObjectMapper objectMapper,
                               @Autowired(required = false) RedisTemplate<String, String> redisTemplate) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public UUID handle(CreateOrderCommand command) {
        log.info("Handling CreateOrderCommand for customer: {}", command.customerId());

        UUID orderId = UUID.randomUUID();
        List<OrderItem> items = command.items().stream()
                .map(item -> new OrderItem(item.productId(), item.quantity(), item.unitPrice()))
                .toList();

        Order order = new Order(orderId, command.customerId(), items);

        // 1. Save write-model to PostgreSQL
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
        orderRepository.save(entity);

        // 2. Persist event to Transactional Outbox (Guarantees atomic write & publish)
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerId(),
                order.getItems().stream()
                        .map(i -> new OrderCreatedEvent.ItemPayload(i.productId(), i.quantity(), i.unitPrice()))
                        .toList(),
                order.getTotalAmount(),
                Instant.now()
        );

        saveToOutbox("Order", orderId.toString(), event.eventType(), event);
        log.info("Order successfully created with ID: {} and queued in Outbox", orderId);

        return orderId;
    }

    @Transactional
    public void handle(CancelOrderCommand command) {
        log.info("Handling CancelOrderCommand for order: {}", command.orderId());

        OrderJpaEntity entity = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + command.orderId()));

        if (entity.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order {} is already cancelled", command.orderId());
            return;
        }

        entity.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(entity);

        List<OrderCreatedEvent.ItemPayload> items = entity.getItems().stream()
                .map(i -> new OrderCreatedEvent.ItemPayload(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();

        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID(),
                command.orderId(),
                command.reason(),
                items,
                Instant.now()
        );

        saveToOutbox("Order", command.orderId().toString(), event.eventType(), event);
        evictOrderViewCacheAfterCommit(command.orderId());
        log.info("Order {} cancelled and cancellation event queued in Outbox with {} items for compensation", command.orderId(), items.size());
    }

    @Transactional
    public void confirmOrder(UUID orderId) {
        log.info("Handling confirmOrder for order: {}", orderId);
        OrderJpaEntity entity = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));
        if (entity.getStatus() == OrderStatus.CONFIRMED) {
            log.warn("Order {} is already confirmed", orderId);
            return;
        }
        entity.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(entity);

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                UUID.randomUUID(),
                entity.getId(),
                entity.getCustomerId(),
                entity.getTotalAmount(),
                Instant.now()
        );
        saveToOutbox("Order", orderId.toString(), event.eventType(), event);
        evictOrderViewCacheAfterCommit(orderId);
        log.info("Order {} confirmed by Saga Orchestrator and OrderConfirmedEvent queued in Outbox", orderId);
    }

    @Transactional
    public void completeOrder(UUID orderId) {
        log.info("Handling completeOrder for order: {}", orderId);
        OrderJpaEntity entity = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));
        if (entity.getStatus() == OrderStatus.COMPLETED) {
            log.warn("Order {} is already completed", orderId);
            return;
        }
        if (entity.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order {} is cancelled and cannot be completed", orderId);
            return;
        }
        entity.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(entity);

        OrderCompletedEvent event = new OrderCompletedEvent(
                UUID.randomUUID(),
                entity.getId(),
                entity.getCustomerId(),
                entity.getTotalAmount(),
                Instant.now()
        );
        saveToOutbox("Order", orderId.toString(), event.eventType(), event);
        evictOrderViewCacheAfterCommit(orderId);
        log.info("Order {} marked COMPLETED after inventory commit and OrderCompletedEvent queued in Outbox", orderId);
    }

    @Transactional
    public void resetAll() {
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
        if (redisTemplate != null) {
            try {
                var keys = redisTemplate.keys("order:view:*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.warn("Failed to clear order CQRS keys from Redis during reset: {}", e.getMessage());
            }
        }
        log.info("Cleared all orders, order_items, outbox rows, and order CQRS Redis cache keys.");
    }

    private void evictOrderViewCacheAfterCommit(UUID orderId) {
        if (redisTemplate == null) {
            return;
        }
        String cacheKey = "order:view:" + orderId;
        Runnable evictAction = () -> {
            try {
                redisTemplate.delete(cacheKey);
                log.debug("Evicted stale CQRS Redis cache key: {}", cacheKey);
            } catch (Exception e) {
                log.warn("Failed to evict Redis cache key {}: {}", cacheKey, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictAction.run();
                }
            });
        } else {
            evictAction.run();
        }
    }

    private void saveToOutbox(String aggregateType, String aggregateId, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxJpaEntity outbox = new OutboxJpaEntity(
                    UUID.randomUUID(),
                    aggregateType,
                    aggregateId,
                    eventType,
                    payload
            );
            outboxRepository.save(outbox);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }
}
