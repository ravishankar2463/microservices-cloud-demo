package com.ravi.payment.application.service;

import com.ravi.payment.application.command.ProcessPaymentCommand;
import com.ravi.payment.application.port.out.PaymentProviderPort;
import com.ravi.payment.domain.event.PaymentCapturedEvent;
import com.ravi.payment.domain.event.PaymentFailedEvent;
import com.ravi.payment.domain.model.Payment;
import com.ravi.payment.domain.model.PaymentResult;
import com.ravi.payment.domain.model.PaymentStatus;
import com.ravi.payment.domain.repository.PaymentRepository;
import com.ravi.payment.infrastructure.messaging.producer.PaymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProviderPort paymentProviderPort;
    private final PaymentEventPublisher eventPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentProviderPort paymentProviderPort,
            PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentProviderPort = paymentProviderPort;
        this.eventPublisher = eventPublisher;
    }

    public Payment processPayment(ProcessPaymentCommand command) {
        log.info("Processing payment for order {} [IdempotencyKey: {}]", command.orderId(), command.idempotencyKey());

        // Idempotency check: prevent duplicate charges
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Returning existing payment for idempotency key {}", command.idempotencyKey());
            return existing.get();
        }

        Payment payment = new Payment(
            command.orderId(),
            command.customerId(),
            command.amount(),
            command.currency(),
            command.idempotencyKey(),
            command.paymentMethod()
        );
        payment = paymentRepository.save(payment);

        // Invoke payment gateway adapter port
        PaymentResult result = paymentProviderPort.authorizeAndCapture(command);

        if (result.success()) {
            payment.markCaptured(result.transactionReference());
            payment = paymentRepository.save(payment);
            eventPublisher.publishPaymentCaptured(new PaymentCapturedEvent(
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getTransactionReference()
            ));
        } else if (result.status() == PaymentStatus.DECLINED) {
            payment.markDeclined(result.failureReason());
            payment = paymentRepository.save(payment);
            eventPublisher.publishPaymentFailed(new PaymentFailedEvent(
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getFailureReason()
            ));
        } else {
            payment.markFailed(result.failureReason());
            payment = paymentRepository.save(payment);
            eventPublisher.publishPaymentFailed(new PaymentFailedEvent(
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getFailureReason()
            ));
        }

        return payment;
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentById(Long id) {
        return paymentRepository.findById(id);
    }
}
