package com.ravi.order.presentation.rest.controller;

import com.ravi.order.application.command.CancelOrderCommand;
import com.ravi.order.application.command.CreateOrderCommand;
import com.ravi.order.application.command.handler.OrderCommandHandler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Order Commands (Write-Side CQRS).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderCommandController {

    private final OrderCommandHandler commandHandler;

    public OrderCommandController(OrderCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderCommand command) {
        UUID orderId = commandHandler.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "orderId", orderId.toString(),
                "message", "Order placed successfully"
        ));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Customer requested cancellation";
        commandHandler.handle(new CancelOrderCommand(id, reason));
        return ResponseEntity.ok(Map.of(
                "success", true,
                "orderId", id.toString(),
                "message", "Order cancelled successfully"
        ));
    }
}
