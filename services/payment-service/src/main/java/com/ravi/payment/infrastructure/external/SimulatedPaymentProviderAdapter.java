package com.ravi.payment.infrastructure.external;

import com.ravi.payment.application.command.ProcessPaymentCommand;
import com.ravi.payment.application.port.out.PaymentProviderPort;
import com.ravi.payment.domain.model.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class SimulatedPaymentProviderAdapter implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentProviderAdapter.class);

    @Override
    public PaymentResult authorizeAndCapture(ProcessPaymentCommand command) {
        log.info("Processing simulated payment for order {} with amount {}{}",
            command.orderId(), command.amount(), command.currency() != null ? command.currency() : "USD");

        BigDecimal amount = command.amount();
        // Deterministic simulation rules:
        // 1. Amounts ending with .99 trigger Insufficient Funds (Decline)
        // 2. Amounts ending with .50 trigger Gateway Timeout (Failure)
        // 3. All other amounts succeed
        String amountStr = amount.toPlainString();

        if (amountStr.endsWith(".99")) {
            log.warn("Simulated payment DECLINED for order {}: Insufficient funds", command.orderId());
            return PaymentResult.declined("Card declined: Insufficient funds (Simulated trigger .99)");
        }

        if (amountStr.endsWith(".50")) {
            log.warn("Simulated payment FAILED for order {}: Gateway timeout", command.orderId());
            return PaymentResult.failed("Payment gateway communication error (Simulated trigger .50)");
        }

        String txnRef = "TXN_SIM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Simulated payment CAPTURED for order {} with reference {}", command.orderId(), txnRef);
        return PaymentResult.captured(txnRef);
    }
}
