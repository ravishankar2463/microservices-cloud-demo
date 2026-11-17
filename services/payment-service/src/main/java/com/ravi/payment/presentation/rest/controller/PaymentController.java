package com.ravi.payment.presentation.rest.controller;

import com.ravi.payment.application.command.ProcessPaymentCommand;
import com.ravi.payment.application.service.PaymentService;
import com.ravi.payment.domain.model.Payment;
import com.ravi.payment.domain.model.PaymentStatus;
import com.ravi.payment.presentation.rest.response.PaymentResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
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
    public ResponseEntity<PaymentResponseDto> getByOrderId(@PathVariable String orderId) {
        return paymentService.getPaymentByOrderId(orderId)
                .map(PaymentResponseDto::fromDomain)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponseDto> getById(@PathVariable Long id) {
        return paymentService.getPaymentById(id)
                .map(PaymentResponseDto::fromDomain)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
