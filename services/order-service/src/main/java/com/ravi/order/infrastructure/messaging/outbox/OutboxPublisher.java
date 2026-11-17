package com.ravi.order.infrastructure.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled Outbox Publisher implementing the Transactional Outbox Pattern.
 * Polls uncommitted events from outbox table and publishes to Apache Kafka topic.
 */
@Component
@EnableScheduling
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String orderTopic;

    public OutboxPublisher(
            OutboxJpaRepository outboxRepository,
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
            @Value("${kafka.topic.order-events:order-events}") String orderTopic) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.orderTopic = orderTopic;
    }

    @Scheduled(fixedDelayString = "${outbox.poll.interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        if (kafkaTemplate == null) {
            log.warn("OutboxPublisher: KafkaTemplate is null, cannot publish outbox events.");
            return;
        }
        List<OutboxJpaEntity> pendingEvents = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING");

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending events in Transactional Outbox. Publishing to Kafka topic: {}",
                pendingEvents.size(), orderTopic);

        for (OutboxJpaEntity event : pendingEvents) {
            try {
                // Publish to Kafka with aggregateId as partition key (strict per-order ordering)
                kafkaTemplate.send(orderTopic, event.getAggregateId(), event.getPayload()).get();

                event.setStatus("PUBLISHED");
                event.setProcessedAt(Instant.now());
                outboxRepository.save(event);

                log.info("Published outbox event [{}] for aggregate [{}] to topic [{}]",
                        event.getEventType(), event.getAggregateId(), orderTopic);
            } catch (Exception e) {
                log.error("Failed to publish outbox event [{}]: {}", event.getId(), e.getMessage());
                event.setStatus("FAILED");
                outboxRepository.save(event);
            }
        }
    }
}
