package com.atlas.booking.booking.messaging;

import com.atlas.booking.shared.messaging.EventEnvelope;
import com.atlas.booking.shared.messaging.EventTopics;
import com.atlas.booking.shared.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes Booking domain events to Kafka.
 *
 * Uses {@code @TransactionalEventListener(AFTER_COMMIT)} so the Kafka message is
 * only sent after the DB transaction commits — preventing consumers from seeing an
 * event for a Booking row that does not yet exist (OBS-002, OBS-003).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventPublisher {

    private static final String PRODUCER_NAME = "booking-service";
    private static final int EVENT_VERSION = 1;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Sends {@code BookingCreated} to {@link EventTopics#BOOKING_CREATED}.
     * Partition key = {@code bookingId} — guarantees ordering per booking (partitioning.md).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCreated(BookingCreatedApplicationEvent event) {
        var envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                "BookingCreated",
                EVENT_VERSION,
                Instant.now(),
                resolveTraceId(),
                event.getCorrelationId(),
                event.getSagaId().toString(),
                PRODUCER_NAME,
                event.getPayload()
        );

        kafkaTemplate
                .send(EventTopics.BOOKING_CREATED, event.getBookingId().toString(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish BookingCreated: bookingId={}, correlationId={}",
                                event.getBookingId(), event.getCorrelationId(), ex);
                    } else {
                        log.info("BookingCreated published: bookingId={}, partition={}, offset={}",
                                event.getBookingId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Sends BookingConfirmed, BookingFailed or BookingExpired to their respective topics.
     * Partition key = {@code bookingId} — guarantees ordering per booking (partitioning.md).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingLifecycle(BookingLifecycleApplicationEvent event) {
        var envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                event.getEventType(),
                EVENT_VERSION,
                Instant.now(),
                resolveTraceId(),
                event.getCorrelationId(),
                event.getSagaId(),
                PRODUCER_NAME,
                event.getPayload()
        );

        kafkaTemplate
                .send(event.getKafkaTopic(), event.getBookingId().toString(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {}: bookingId={}, correlationId={}",
                                event.getEventType(), event.getBookingId(), event.getCorrelationId(), ex);
                    } else {
                        log.info("{} published: bookingId={}, partition={}, offset={}",
                                event.getEventType(),
                                event.getBookingId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /** Reads traceId from MDC (set by {@link CorrelationIdFilter}), falls back to a new UUID. */
    private String resolveTraceId() {
        String traceId = MDC.get(CorrelationIdFilter.TRACE_ID_MDC_KEY);
        return (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString();
    }
}
