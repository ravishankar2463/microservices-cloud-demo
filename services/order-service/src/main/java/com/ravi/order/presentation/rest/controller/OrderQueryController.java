package com.ravi.order.presentation.rest.controller;

import com.ravi.order.application.query.GetOrderByIdQuery;
import com.ravi.order.application.query.handler.OrderQueryHandler;
import com.ravi.order.application.query.model.OrderView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for Order Queries (Read-Side CQRS).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderQueryController {

    private final OrderQueryHandler queryHandler;

    public OrderQueryController(OrderQueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderView> getOrderById(@PathVariable UUID id) {
        return queryHandler.handle(new GetOrderByIdQuery(id))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
