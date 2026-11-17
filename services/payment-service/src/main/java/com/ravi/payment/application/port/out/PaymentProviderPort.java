package com.ravi.payment.application.port.out;

import com.ravi.payment.application.command.ProcessPaymentCommand;
import com.ravi.payment.domain.model.PaymentResult;

public interface PaymentProviderPort {
    PaymentResult authorizeAndCapture(ProcessPaymentCommand command);
}
