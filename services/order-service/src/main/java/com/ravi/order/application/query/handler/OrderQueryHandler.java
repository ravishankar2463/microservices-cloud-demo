package com.ravi.order.application.query.handler;

import com.ravi.order.application.query.GetOrderByIdQuery;
import com.ravi.order.application.query.model.OrderView;
import com.ravi.order.infrastructure.persistence.entity.OrderItemJpaEntity;
import com.ravi.order.infrastructure.persistence.entity.OrderJpaEntity;
import com.ravi.order.infrastructure.persistence.repository.OrderJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Application Query Handler executing CQRS Read operations.
 * Implements Cache-Aside pattern with Redis for high-throughput reads.
 */
@Service
public class OrderQueryHandler {

    private final OrderJpaRepository orderRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public OrderQueryHandler(OrderJpaRepository orderRepository,
                             @Autowired(required = false) RedisTemplate<String, String> redisTemplate,
                             ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<OrderView> handle(GetOrderByIdQuery query) {
        String cacheKey = "order:view:" + query.orderId();

        if (redisTemplate != null) {
            try {
                String cachedJson = redisTemplate.opsForValue().get(cacheKey);
                if (cachedJson != null) {
                    return Optional.of(objectMapper.readValue(cachedJson, OrderView.class));
                }
            } catch (Exception ignored) {
                // Redis fallback to database
            }
        }

        return orderRepository.findById(query.orderId()).map(entity -> {
            OrderView view = toOrderView(entity);
            if (redisTemplate != null) {
                try {
                    String json = objectMapper.writeValueAsString(view);
                    redisTemplate.opsForValue().set(cacheKey, json, Duration.ofMinutes(10));
                } catch (Exception ignored) {}
            }
            return view;
        });
    }

    private OrderView toOrderView(OrderJpaEntity entity) {
        List<OrderView.ItemView> items = entity.getItems().stream()
                .map(this::toItemView)
                .toList();

        return new OrderView(
                entity.getId(),
                entity.getCustomerId(),
                entity.getTotalAmount(),
                entity.getStatus().name(),
                entity.getCreatedAt(),
                items
        );
    }

    private OrderView.ItemView toItemView(OrderItemJpaEntity item) {
        return new OrderView.ItemView(
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }
}
