package com.ravi.payment.infrastructure.messaging.producer;

import com.ravi.payment.domain.event.PaymentCapturedEvent;
import com.ravi.payment.domain.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    public static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(@Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public void publishPaymentCaptured(PaymentCapturedEvent event) {
        log.info("Emitting PaymentCapturedEvent for order {}", event.orderId());
        if (kafkaTemplate != null) {
            try {
                String payload = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, event.orderId(), payload);
                log.info("Published PaymentCapturedEvent JSON payload to topic {}: {}", PAYMENT_EVENTS_TOPIC, payload);
            } catch (Exception e) {
                log.warn("Kafka not reachable to send PaymentCapturedEvent: {}", e.getMessage());
            }
        }
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        log.info("Emitting PaymentFailedEvent for order {}: {}", event.orderId(), event.failureReason());
        if (kafkaTemplate != null) {
            try {
                String payload = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, event.orderId(), payload);
                log.info("Published PaymentFailedEvent JSON payload to topic {}: {}", PAYMENT_EVENTS_TOPIC, payload);
            } catch (Exception e) {
                log.warn("Kafka not reachable to send PaymentFailedEvent: {}", e.getMessage());
            }
        }
    }
}
