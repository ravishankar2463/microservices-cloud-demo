package com.ravi.inventory.presentation.rest.controller;

import com.ravi.inventory.application.service.InventoryReservationService;
import com.ravi.inventory.presentation.rest.model.StockOperationResponse;
import com.ravi.inventory.presentation.rest.model.StockReservationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory Management", description = "Operations for checking inventory levels, distributed stock reservation with Redis locks, and rollback compensation")
public class InventoryController {

    private final InventoryReservationService reservationService;

    public InventoryController(InventoryReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/{productId}")
    @Operation(
            summary = "Get stock levels for a product",
            description = "Fetches the current available stock and active reserved stock count for a given SKU/product ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Product inventory found",
                    content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"data\":{\"productId\":\"PROD-TECH-001\",\"name\":\"Mechanical Gaming Keyboard\",\"availableQuantity\":48,\"reservedQuantity\":2}}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not found in inventory registry",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<?> getStock(
            @Parameter(description = "Product SKU or unique ID", example = "PROD-TECH-001")
            @PathVariable String productId) {
        return reservationService.getItem(productId)
                .<ResponseEntity<?>>map(item -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "data", item
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "Product not found: " + productId
                )));
    }

    @PostMapping("/reserve")
    @Operation(
            summary = "Reserve stock with distributed lock",
            description = "Acquires Redis distributed lock (SET NX PX 5000) on the SKU and safely decrements available quantity if sufficient units exist."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Stock reserved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockOperationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Insufficient stock or lock acquisition contention timeout",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<?> reserveStock(@Valid @RequestBody StockReservationRequest request) {
        String productId = request.productId();
        int quantity = request.quantity() > 0 ? request.quantity() : 1;

        boolean success = reservationService.reserveStock(productId, quantity);
        if (success) {
            return ResponseEntity.ok(new StockOperationResponse(
                    true,
                    "Stock reserved successfully",
                    productId,
                    quantity
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "error", "Stock reservation failed (insufficient stock or lock contention)"
            ));
        }
    }

    @PostMapping("/release")
    @Operation(
            summary = "Compensate / Release reserved stock",
            description = "Saga compensating step: restores reserved quantity back to available stock in Redis upon order failure or customer cancellation."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Stock successfully released / restored",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockOperationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Release failed due to invalid state",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<?> releaseStock(@Valid @RequestBody StockReservationRequest request) {
        String productId = request.productId();
        int quantity = request.quantity() > 0 ? request.quantity() : 1;

        boolean success = reservationService.releaseStock(productId, quantity);
        if (success) {
            return ResponseEntity.ok(new StockOperationResponse(
                    true,
                    "Stock released / compensated successfully",
                    productId,
                    quantity
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "Stock compensation release failed"
            ));
        }
    }

    @PostMapping("/reset")
    @Operation(
            summary = "Reset inventory stock levels",
            description = "Restores all initial catalog stock levels to 50 units and clears all active reservation locks."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Inventory stock successfully reset",
            content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"Inventory restored to initial stock levels and active reservations cleared\"}"))
    )
    public ResponseEntity<?> resetInventory() {
        reservationService.resetAll();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Inventory restored to initial stock levels and active reservations cleared"
        ));
    }
}
