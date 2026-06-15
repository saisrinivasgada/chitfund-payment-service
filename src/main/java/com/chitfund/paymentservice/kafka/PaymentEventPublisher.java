package com.chitfund.paymentservice.kafka;

import com.chitfund.common.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around KafkaTemplate.
 *
 * WHY not call KafkaTemplate directly from the service?
 * Single responsibility: services focus on business logic, this class handles
 * the "how to publish" concern. If we switch from Kafka to Pulsar or SNS later,
 * only this file changes.
 *
 * WHY try-catch instead of letting exceptions propagate?
 * The DB write already happened (the @Transactional in the service committed).
 * If Kafka is temporarily down, we should NOT roll back the payment — that would
 * create an inconsistency between what the admin sees and what the DB says.
 * We accept at-most-once delivery here. Production fix: Transactional Outbox Pattern
 * (write event to DB table in same transaction, CDC tool reads and publishes to Kafka).
 *
 * INTERVIEW: "We use best-effort publishing for now. The Transactional Outbox Pattern
 * (Debezium + Kafka Connect) guarantees at-least-once delivery with no dual-write problem.
 * It's on the roadmap but overkill for our current scale."
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(ChitMonthOpenedEvent event) {
        send(KafkaTopics.MONTH_OPENED, event.chitId(), event);
    }

    public void publish(ChitMonthSkippedEvent event) {
        send(KafkaTopics.MONTH_SKIPPED, event.chitId(), event);
    }

    public void publish(CashCollectedEvent event) {
        send(KafkaTopics.CASH_COLLECTED, event.batchId(), event);
    }

    public void publish(PaymentCompletedEvent event) {
        send(KafkaTopics.PAYMENT_COMPLETED, event.batchId(), event);
    }

    private void send(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event);
            log.debug("Published {} to topic {}", event.getClass().getSimpleName(), topic);
        } catch (Exception e) {
            log.warn("Failed to publish {} to topic {}: {}", event.getClass().getSimpleName(), topic, e.getMessage());
        }
    }
}
