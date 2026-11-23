package com.ravi.payment.infrastructure.external;

import com.ravi.payment.application.command.ProcessPaymentCommand;
import com.ravi.payment.application.port.out.PaymentProviderPort;
import com.ravi.payment.domain.model.PaymentResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class SimulatedPaymentProviderAdapter implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentProviderAdapter.class);

    @Override
    @CircuitBreaker(name = "externalPaymentGateway", fallbackMethod = "paymentFallback")
    @Retry(name = "externalPaymentGateway")
    public PaymentResult authorizeAndCapture(ProcessPaymentCommand command) {
        log.info("Processing payment via external gateway for order {} with amount {}{}",
            command.orderId(), command.amount(), command.currency() != null ? command.currency() : "USD");

        BigDecimal amount = command.amount();
        String amountStr = amount.toPlainString();

        // Deterministic simulation rules:
        // 1. Amounts ending with .99 trigger Insufficient Funds (Decline)
        // 2. Amounts ending with .50 trigger Gateway Timeout (Failure -> Circuit Breaker trips on repeated occurrences)
        // 3. All other amounts succeed
        if (amountStr.endsWith(".99")) {
            log.warn("Payment DECLINED by external gateway for order {}: Insufficient funds", command.orderId());
            return PaymentResult.declined("Card declined: Insufficient funds (Simulated trigger .99)");
        }

        if (amountStr.endsWith(".50")) {
            log.warn("Payment gateway communication failure for order {}: Gateway timeout", command.orderId());
            throw new PaymentGatewayException("Payment gateway communication error (Simulated trigger .50)");
        }

        String txnRef = "TXN_SIM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Payment CAPTURED by external gateway for order {} with reference {}", command.orderId(), txnRef);
        return PaymentResult.captured(txnRef);
    }

    public PaymentResult paymentFallback(ProcessPaymentCommand command, Throwable t) {
        log.warn("Payment circuit breaker OPEN / fallback triggered for order {}: {}", command.orderId(), t.getMessage());
        return PaymentResult.failed("Payment gateway communication error (Circuit Breaker OPEN): " + t.getMessage());
    }
}
