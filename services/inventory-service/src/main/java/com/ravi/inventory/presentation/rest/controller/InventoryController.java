package com.ravi.inventory.presentation.rest.controller;

import com.ravi.inventory.application.service.InventoryReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryReservationService reservationService;

    public InventoryController(InventoryReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<?> getStock(@PathVariable String productId) {
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
    public ResponseEntity<?> reserveStock(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        Number qtyNum = (Number) request.getOrDefault("quantity", 1);
        int quantity = qtyNum.intValue();

        boolean success = reservationService.reserveStock(productId, quantity);
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Stock reserved successfully",
                    "productId", productId,
                    "quantity", quantity
            ));
        } else {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "error", "Stock reservation failed (insufficient stock or lock contention)"
            ));
        }
    }

    @PostMapping("/release")
    public ResponseEntity<?> releaseStock(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        Number qtyNum = (Number) request.getOrDefault("quantity", 1);
        int quantity = qtyNum.intValue();

        boolean success = reservationService.releaseStock(productId, quantity);
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Stock released / compensated successfully",
                    "productId", productId,
                    "quantity", quantity
            ));
        } else {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "error", "Stock compensation release failed"
            ));
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetInventory() {
        reservationService.resetAll();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Inventory restored to initial stock levels and active reservations cleared"
        ));
    }
}
