package com.ravi.payment.presentation.rest.controller;

import com.ravi.payment.application.command.ProcessPaymentCommand;
import com.ravi.payment.application.service.PaymentService;
import com.ravi.payment.domain.model.Payment;
import com.ravi.payment.domain.model.PaymentStatus;
import com.ravi.payment.presentation.rest.response.PaymentResponseDto;
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

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payment Operations", description = "Endpoints for processing authorizations, querying transaction state, and idempotency checks")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    @Operation(
            summary = "Process and authorize payment",
            description = "Simulates authorization and settlement. In this demo, amounts ending in .99 trigger simulated DECLINED (HTTP 402), while amounts ending in .50 trigger simulated TIMEOUT (HTTP 502)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Payment successfully authorized and captured",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "402",
                    description = "Payment authorization declined by issuing bank (triggering Saga rollback)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload or validation constraint violation",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Downstream banking gateway timeout or simulation failure",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponseDto.class))
            )
    })
    public ResponseEntity<PaymentResponseDto> processPayment(@Valid @RequestBody ProcessPaymentCommand command) {
        Payment payment = paymentService.processPayment(command);
        PaymentResponseDto response = PaymentResponseDto.fromDomain(payment);
        if (response.status() == PaymentStatus.CAPTURED) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else if (response.status() == PaymentStatus.DECLINED) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
        }
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payment transaction by Order ID", description = "Queries payment audit history for a given order ID.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment transaction found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No payment transaction found for the specified order",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<PaymentResponseDto> getByOrderId(
            @Parameter(description = "Order UUID string", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable String orderId) {
        return paymentService.getPaymentByOrderId(orderId)
                .map(PaymentResponseDto::fromDomain)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by payment ID", description = "Queries payment audit history by internal database ID.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment transaction found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Payment transaction not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<PaymentResponseDto> getById(
            @Parameter(description = "Payment record ID", example = "1")
            @PathVariable Long id) {
        return paymentService.getPaymentById(id)
                .map(PaymentResponseDto::fromDomain)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset payment records", description = "Clears all payment records and idempotency tracking caches.")
    @ApiResponse(
            responseCode = "200",
            description = "Payment tables successfully reset",
            content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"All payment transactions and idempotency records cleared\"}"))
    )
    public ResponseEntity<java.util.Map<String, Object>> resetPayments() {
        paymentService.resetAll();
        return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "All payment transactions and idempotency records cleared"
        ));
    }
}
