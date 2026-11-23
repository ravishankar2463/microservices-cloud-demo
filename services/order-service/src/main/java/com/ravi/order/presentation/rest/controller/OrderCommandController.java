package com.ravi.order.presentation.rest.controller;

import com.ravi.order.application.command.CancelOrderCommand;
import com.ravi.order.application.command.CreateOrderCommand;
import com.ravi.order.application.command.handler.OrderCommandHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Order Commands (Write-Side CQRS).
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Order Commands", description = "Write-side CQRS operations: placing orders, cancelling orders, and resetting state")
public class OrderCommandController {

    private final OrderCommandHandler commandHandler;

    public OrderCommandController(OrderCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @PostMapping
    @Operation(
            summary = "Place a new customer order",
            description = "Initiates the distributed e-commerce Saga workflow. Atomically persists Order aggregate & Transactional Outbox event in PostgreSQL, relays event to Kafka/SQS, and begins inventory reservation."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Order accepted and placed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(example = "{\"success\":true,\"orderId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"message\":\"Order placed successfully\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid command payload or schema validation failure",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal database or transaction processing failure",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderCommand command) {
        UUID orderId = commandHandler.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "orderId", orderId.toString(),
                "message", "Order placed successfully"
        ));
    }

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Cancel an active order",
            description = "Triggers customer-initiated compensation. Cancels the order in PostgreSQL and emits OrderCancelledEvent to compensate/release reserved inventory stock in Redis."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order successfully marked as cancelled",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(example = "{\"success\":true,\"orderId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"message\":\"Order cancelled successfully\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Order not found or already terminal",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @Parameter(description = "UUID of the order to cancel", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
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

    @PostMapping("/reset")
    @Operation(
            summary = "Reset order database and cache",
            description = "Development/testing utility: truncates all orders, outbox events, and evicts CQRS query cache entries."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Orders and cache reset successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"All orders, outbox events, and CQRS cache entries cleared\"}"))
    )
    public ResponseEntity<Map<String, Object>> resetOrders() {
        commandHandler.resetAll();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All orders, outbox events, and CQRS cache entries cleared"
        ));
    }
}
