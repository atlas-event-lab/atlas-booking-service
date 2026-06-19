package com.atlas.booking.booking.messaging;

import com.atlas.booking.booking.entity.OutboxEvent;
import com.atlas.booking.booking.entity.OutboxStatus;
import com.atlas.booking.booking.repository.OutboxRepository;
import com.atlas.booking.shared.messaging.EventTopics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Phase-1 outbox relay (EVT-009): a scheduled poller that publishes outbox rows to Kafka.
 * <p>
 * Reads PENDING / FAILED rows oldest-first, publishes each envelope, and marks it
 * PUBLISHED. A failed publish is marked FAILED and retried on a later poll. Delivery is
 * therefore at-least-once — consumers deduplicate on the envelope {@code eventId}
 * (coding-standards §Outbox & Event Publishing). A future CDC/Debezium relay replaces
 * this poller (project.md Future Evolution).
 * <p>
 * The job is idempotent and uses {@code fixedDelay}, so a slow run never overlaps the next
 * (coding-standards §Spring Boot — "Scheduled jobs SHALL be idempotent").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final List<OutboxStatus> UNPUBLISHED =
            List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${atlas.outbox.poll-interval-ms:2000}")
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.findTop100ByStatusInOrderByCreatedAtAsc(UNPUBLISHED);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Outbox relay processing {} event(s)", batch.size());
        for (OutboxEvent event : batch) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            JsonNode envelope = objectMapper.readTree(event.getPayload());
            String topic = resolveTopic(event.getEventType());

            // Block until the broker acknowledges so the row is only marked PUBLISHED on success.
            kafkaTemplate.send(topic, event.getAggregateId().toString(), envelope).get();

            event.markPublished(Instant.now());
            outboxRepository.save(event);
            log.info("Outbox event published: id={}, eventType={}, aggregateId={}",
                    event.getId(), event.getEventType(), event.getAggregateId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(event, e);
        } catch (Exception e) {
            markFailed(event, e);
        }
    }

    private void markFailed(OutboxEvent event, Exception e) {
        event.markFailed();
        outboxRepository.save(event);
        log.error("Failed to publish outbox event: id={}, eventType={}, attempts={}",
                event.getId(), event.getEventType(), event.getAttempts(), e);
    }

    /** Maps an event type to its owning Booking topic (topics.md). */
    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "BookingCreated"   -> EventTopics.BOOKING_CREATED;
            case "BookingConfirmed" -> EventTopics.BOOKING_CONFIRMED;
            case "BookingFailed"    -> EventTopics.BOOKING_FAILED;
            case "BookingExpired"   -> EventTopics.BOOKING_EXPIRED;
            default -> throw new IllegalStateException("No topic mapping for event type: " + eventType);
        };
    }
}
