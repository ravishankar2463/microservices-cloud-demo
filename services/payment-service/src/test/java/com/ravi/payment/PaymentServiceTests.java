package com.ravi.payment;

import com.ravi.payment.application.command.ProcessPaymentCommand;
import com.ravi.payment.application.service.PaymentService;
import com.ravi.payment.domain.model.Payment;
import com.ravi.payment.domain.model.PaymentStatus;
import com.ravi.payment.domain.repository.PaymentRepository;
import com.ravi.payment.infrastructure.external.SimulatedPaymentProviderAdapter;
import com.ravi.payment.infrastructure.messaging.producer.PaymentEventPublisher;
import com.ravi.payment.presentation.rest.controller.PaymentController;
import com.ravi.payment.presentation.rest.response.PaymentResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceTests {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SimulatedPaymentProviderAdapter paymentProviderAdapter;

    private PaymentService paymentService;
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        PaymentEventPublisher noopPublisher = new PaymentEventPublisher(null);
        paymentService = new PaymentService(paymentRepository, paymentProviderAdapter, noopPublisher);
        paymentController = new PaymentController(paymentService);
    }

    @Test
    @DisplayName("Should capture payment successfully for regular amount")
    void shouldCapturePaymentSuccessfully() {
        ProcessPaymentCommand command = new ProcessPaymentCommand(
            "ORD-" + UUID.randomUUID(),
            "CUST-001",
            new BigDecimal("150.00"),
            "USD",
            "IDEM-" + UUID.randomUUID(),
            "CREDIT_CARD"
        );

        Payment response = paymentService.processPayment(command);
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(response.getTransactionReference()).startsWith("TXN_SIM_");
        assertThat(response.getFailureReason()).isNull();

        Optional<Payment> byOrder = paymentService.getPaymentByOrderId(command.orderId());
        assertThat(byOrder).isPresent();
        assertThat(byOrder.get().getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    @DisplayName("Should decline payment deterministically when amount ends with .99")
    void shouldDeclinePaymentWhenInsufficientFunds() {
        ProcessPaymentCommand command = new ProcessPaymentCommand(
            "ORD-FAIL-" + UUID.randomUUID(),
            "CUST-002",
            new BigDecimal("19.99"), // Trigger for decline
            "USD",
            "IDEM-" + UUID.randomUUID(),
            "CREDIT_CARD"
        );

        Payment response = paymentService.processPayment(command);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(response.getFailureReason()).contains("Insufficient funds");
        assertThat(response.getTransactionReference()).isNull();
    }

    @Test
    @DisplayName("Should fail payment deterministically when amount ends with .50 (Gateway timeout)")
    void shouldFailPaymentOnGatewayTimeout() {
        ProcessPaymentCommand command = new ProcessPaymentCommand(
            "ORD-TIMEOUT-" + UUID.randomUUID(),
            "CUST-003",
            new BigDecimal("49.50"), // Trigger for gateway timeout
            "USD",
            "IDEM-" + UUID.randomUUID(),
            "CREDIT_CARD"
        );

        Payment response = paymentService.processPayment(command);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).contains("gateway communication error");
        assertThat(response.getTransactionReference()).isNull();
    }

    @Test
    @DisplayName("Should enforce strict idempotency and not charge twice for identical idempotency key")
    void shouldEnforceStrictIdempotency() {
        String idempotencyKey = "IDEM-" + UUID.randomUUID();
        String orderId = "ORD-" + UUID.randomUUID();

        ProcessPaymentCommand command1 = new ProcessPaymentCommand(
            orderId, "CUST-001", new BigDecimal("75.00"), "USD", idempotencyKey, "CREDIT_CARD"
        );

        Payment firstResponse = paymentService.processPayment(command1);
        assertThat(firstResponse.getStatus()).isEqualTo(PaymentStatus.CAPTURED);

        ProcessPaymentCommand command2 = new ProcessPaymentCommand(
            orderId, "CUST-001", new BigDecimal("75.00"), "USD", idempotencyKey, "CREDIT_CARD"
        );

        Payment secondResponse = paymentService.processPayment(command2);
        assertThat(secondResponse.getId()).isEqualTo(firstResponse.getId());
        assertThat(secondResponse.getTransactionReference()).isEqualTo(firstResponse.getTransactionReference());
    }

    @Test
    @DisplayName("Should return appropriate HTTP status codes in PaymentController")
    void shouldReturnCorrectHttpStatusCodes() {
        // Success -> 201 CREATED
        ProcessPaymentCommand okCommand = new ProcessPaymentCommand(
            "ORD-HTTP-1", "CUST-001", new BigDecimal("100.00"), "USD", "IDEM-HTTP-1", "CREDIT_CARD"
        );
        ResponseEntity<PaymentResponseDto> okRes = paymentController.processPayment(okCommand);
        assertThat(okRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Declined -> 402 PAYMENT_REQUIRED
        ProcessPaymentCommand declineCommand = new ProcessPaymentCommand(
            "ORD-HTTP-2", "CUST-001", new BigDecimal("10.99"), "USD", "IDEM-HTTP-2", "CREDIT_CARD"
        );
        ResponseEntity<PaymentResponseDto> declineRes = paymentController.processPayment(declineCommand);
        assertThat(declineRes.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);

        // Failed -> 502 BAD_GATEWAY
        ProcessPaymentCommand failCommand = new ProcessPaymentCommand(
            "ORD-HTTP-3", "CUST-001", new BigDecimal("10.50"), "USD", "IDEM-HTTP-3", "CREDIT_CARD"
        );
        ResponseEntity<PaymentResponseDto> failRes = paymentController.processPayment(failCommand);
        assertThat(failRes.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
