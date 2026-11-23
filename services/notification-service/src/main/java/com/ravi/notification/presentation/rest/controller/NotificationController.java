package com.ravi.notification.presentation.rest.controller;

import com.ravi.notification.application.service.DeadLetterQueueService;
import com.ravi.notification.application.service.InvoiceArchivalService;
import com.ravi.notification.infrastructure.messaging.inbox.IdempotentMessageStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notification & DLQ", description = "Endpoints for inspecting archived S3 invoices, reviewing DLQ poisoned messages, and initiating redrives")
public class NotificationController {

    private final InvoiceArchivalService invoiceService;
    private final IdempotentMessageStore idempotentMessageStore;
    private final DeadLetterQueueService dlqService;

    public NotificationController(InvoiceArchivalService invoiceService,
                                  IdempotentMessageStore idempotentMessageStore,
                                  DeadLetterQueueService dlqService) {
        this.invoiceService = invoiceService;
        this.idempotentMessageStore = idempotentMessageStore;
        this.dlqService = dlqService;
    }

    @GetMapping("/invoices/{orderId}")
    @Operation(summary = "Get archived invoice document", description = "Retrieves an invoice generated and archived in LocalStack S3 for a completed order.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Archived invoice found",
                    content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"orderId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"invoice\":\"{\\\"invoiceId\\\":\\\"INV-3fa85f64\\\",\\\"status\\\":\\\"ARCHIVED\\\"}\"}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No invoice found for the given order ID",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<?> getInvoice(
            @Parameter(description = "Order UUID string", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable String orderId) {
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

    @GetMapping("/dlq")
    @Operation(summary = "List parked Dead Letter Queue messages", description = "Returns all failed or poisoned messages parked in the DLQ buffer for analysis.")
    @ApiResponse(
            responseCode = "200",
            description = "List of DLQ messages",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DeadLetterQueueService.DeadLetterMessage.class)))
    )
    public ResponseEntity<List<DeadLetterQueueService.DeadLetterMessage>> getDlqMessages() {
        return ResponseEntity.ok(dlqService.getAllMessages());
    }

    @PostMapping("/dlq/redrive/{id}")
    @Operation(summary = "Redrive poisoned message from DLQ", description = "Replays a failed event message, re-attempts invoice archival, and evicts it from the DLQ.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Message replayed and evicted from DLQ successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"messageId\":\"msg-dlq-001\",\"action\":\"REPLAYED_AND_EVICTED\"}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Message not found in DLQ",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<?> redriveMessage(
            @Parameter(description = "Unique ID of the DLQ message to redrive", example = "msg-dlq-001")
            @PathVariable String id) {
        return dlqService.getMessage(id).map(msg -> {
            String invoiceContent = """
                    {
                      "invoiceId": "INV-%s-REPLAY",
                      "orderId": "%s",
                      "status": "REDRIVEN_FROM_DLQ",
                      "source": "DLQ:RedriveAPI"
                    }
                    """.formatted(id, id);
            invoiceService.archiveInvoice(id, invoiceContent);
            dlqService.removeMessage(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "messageId", id,
                    "action", "REPLAYED_AND_EVICTED"
            ));
        }).orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", "Message not found in DLQ: " + id
        )));
    }

    @DeleteMapping("/dlq")
    @Operation(summary = "Clear all DLQ messages", description = "Purges all messages currently waiting in the Dead Letter Queue.")
    @ApiResponse(
            responseCode = "200",
            description = "DLQ purged successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"clearedDlqMessages\":3}"))
    )
    public ResponseEntity<?> clearDlq() {
        int cleared = dlqService.clearAll();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "clearedDlqMessages", cleared
        ));
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset notification service state", description = "Clears LocalStack S3 invoices, DLQ buffer, and idempotency inbox tracking cache.")
    @ApiResponse(
            responseCode = "200",
            description = "Notification stores reset successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"clearedInvoices\":5,\"clearedDlqMessages\":2,\"message\":\"Cleared S3 invoices, local invoice cache, DLQ, and idempotency inbox\"}"))
    )
    public ResponseEntity<?> resetNotifications() {
        int clearedInvoices = invoiceService.resetAll();
        idempotentMessageStore.clearAll();
        int clearedDlq = dlqService.clearAll();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "clearedInvoices", clearedInvoices,
                "clearedDlqMessages", clearedDlq,
                "message", "Cleared S3 invoices, local invoice cache, DLQ, and idempotency inbox"
        ));
    }
}
