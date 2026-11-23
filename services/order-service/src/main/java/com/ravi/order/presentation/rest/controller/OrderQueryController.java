package com.ravi.order.presentation.rest.controller;

import com.ravi.order.application.query.GetOrderByIdQuery;
import com.ravi.order.application.query.handler.OrderQueryHandler;
import com.ravi.order.application.query.model.OrderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Order Queries", description = "Read-side CQRS query projections for retrieving order state")
public class OrderQueryController {

    private final OrderQueryHandler queryHandler;

    public OrderQueryController(OrderQueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get order details by UUID",
            description = "Retrieves the synthesized CQRS read-model order view including status (PENDING, PAID, COMPLETED, CANCELLED), total amount, and line item details."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order found and returned successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderView.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order with the requested ID does not exist",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<OrderView> getOrderById(
            @Parameter(description = "Unique UUID of the order", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return queryHandler.handle(new GetOrderByIdQuery(id))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
