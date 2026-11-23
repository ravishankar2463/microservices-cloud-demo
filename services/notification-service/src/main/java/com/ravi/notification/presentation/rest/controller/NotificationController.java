package com.ravi.notification.presentation.rest.controller;

import com.ravi.notification.application.service.InvoiceArchivalService;
import com.ravi.notification.infrastructure.messaging.inbox.IdempotentMessageStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final InvoiceArchivalService invoiceService;
    private final IdempotentMessageStore idempotentMessageStore;

    public NotificationController(InvoiceArchivalService invoiceService, IdempotentMessageStore idempotentMessageStore) {
        this.invoiceService = invoiceService;
        this.idempotentMessageStore = idempotentMessageStore;
    }

    @GetMapping("/invoices/{orderId}")
    public ResponseEntity<?> getInvoice(@PathVariable String orderId) {
        return invoiceService.getInvoice(orderId)
                .<ResponseEntity<?>>map(invoice -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "orderId", orderId,
                        "invoice", invoice.payload()
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "No invoice found for order ID: " + orderId
                )));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetNotifications() {
        int clearedInvoices = invoiceService.resetAll();
        idempotentMessageStore.clearAll();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "clearedInvoices", clearedInvoices,
                "message", "Cleared S3 invoices, local invoice cache, and idempotency inbox"
        ));
    }
}
