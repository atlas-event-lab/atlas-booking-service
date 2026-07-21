package com.atlas.booking.booking.messaging;

import com.atlas.booking.booking.entity.OutboxEvent;
import com.atlas.booking.booking.repository.OutboxRepository;
import com.atlas.booking.shared.messaging.EventTopics;
import com.atlas.booking.shared.messaging.EventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    /** Max rows claimed per poll — mirrors the {@code LIMIT} in {@code claimBatchForPublishing}. */
    public static final int BATCH_LIMIT = 100;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public int publishPending() {
        // Claim a disjoint batch (FOR UPDATE SKIP LOCKED) so concurrent replicas never publish the
        // same row; the transaction holds the locks until each row is marked below (ADR-0013).
        // Scheduling is driven by OutboxRelayScheduler (adaptive interval + drain-in-loop).
        List<OutboxEvent> batch = outboxRepository.claimBatchForPublishing();
        if (batch.isEmpty()) {
            return 0;
        }
        log.debug("Outbox relay processing {} event(s)", batch.size());

        // Phase 1 — dispatch every send without blocking, so the producer pipelines and batches
        // them onto the wire (one poll = one batched flush, not N serial broker round-trips).
        List<Dispatch> dispatched = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            try {
                String topic = resolveTopic(event.getEventType());
                dispatched.add(new Dispatch(
                        event, kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload())));
            } catch (Exception e) {
                // Nothing left the buffer for this row (e.g. topic resolution) — fail it now.
                event.markFailed();
                log.error(
                        "Failed to dispatch outbox event: id={}, eventType={}, attempts={}",
                        event.getId(),
                        event.getEventType(),
                        event.getAttempts(),
                        e);
            }
        }
        try {
            kafkaTemplate.flush();
        } catch (Exception e) {
            // The per-send futures below still report each row's outcome; don't fail the whole tx.
            log.warn("Outbox relay flush failed; awaiting individual sends", e);
        }

        // Phase 2 — await each broker ack and mark the row by its own outcome. Delivery stays
        // at-least-once (consumers dedupe on eventId); a crash before save re-publishes next poll.
        Instant now = Instant.now();
        for (Dispatch dispatch : dispatched) {
            OutboxEvent event = dispatch.event();
            try {
                dispatch.future().get();
                event.markPublished(now);
                log.debug(
                        "Outbox event published: id={}, eventType={}, aggregateId={}",
                        event.getId(),
                        event.getEventType(),
                        event.getAggregateId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailed();
                log.error(
                        "Interrupted publishing outbox event: id={}, eventType={}, attempts={}",
                        event.getId(),
                        event.getEventType(),
                        event.getAttempts(),
                        e);
            } catch (Exception e) {
                event.markFailed();
                log.error(
                        "Failed to publish outbox event: id={}, eventType={}, attempts={}",
                        event.getId(),
                        event.getEventType(),
                        event.getAttempts(),
                        e);
            }
        }

        // Single write-back for the whole batch instead of a save per row.
        outboxRepository.saveAll(batch);
        return batch.size();
    }

    /** One dispatched send awaiting its broker acknowledgement. */
    private record Dispatch(OutboxEvent event, CompletableFuture<SendResult<String, String>> future) {}

    /** Maps an event type to its owning Booking topic (topics.md). */
    private String resolveTopic(EventType eventType) {
        return switch (eventType) {
            case BOOKING_CREATED -> EventTopics.BOOKING_CREATED;
            case BOOKING_CONFIRMED -> EventTopics.BOOKING_CONFIRMED;
            case BOOKING_CANCELLED -> EventTopics.BOOKING_CANCELLED;
            case BOOKING_FAILED -> EventTopics.BOOKING_FAILED;
            case BOOKING_EXPIRED -> EventTopics.BOOKING_EXPIRED;
        };
    }
}
