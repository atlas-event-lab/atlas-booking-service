package com.atlas.booking.booking.messaging;

import com.atlas.booking.booking.entity.OutboxEvent;
import com.atlas.booking.booking.event.EventEnvelope;
import com.atlas.booking.booking.repository.OutboxRepository;
import com.atlas.booking.shared.messaging.EventType;
import com.atlas.booking.shared.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Writes domain events to the Transactional Outbox (EVT-009).
 * <p>
 * Called from inside a {@code @Transactional} Service method so the outbox row is
 * committed atomically with the booking state change — no Kafka call happens here,
 * avoiding the dual-write (coding-standards §Outbox & Event Publishing). The
 * {@code OutboxRelay} publishes the row afterwards.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private static final String PRODUCER = "booking-service";
    private static final String AGGREGATE_TYPE = "Booking";
    private static final Integer EVENT_VERSION = 1;

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Builds the full event envelope (message-envelope.md) and stores it as a PENDING
     * outbox row. Partition key for publication is {@code aggregateId} (partitioning.md).
     *
     * @param aggregateId   the Booking id (also the Kafka partition key)
     * @param eventType     event name, e.g. {@code BookingCreated}
     * @param correlationId correlation id propagated through the saga (OBS-002)
     * @param sagaId        saga instance id (OBS-003)
     * @param payload       the business payload (never null, never carries metadata)
     */
    public <T> void write(UUID aggregateId, EventType eventType, String correlationId, String sagaId, T payload) {
        EventEnvelope<T> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                eventType.name(),
                EVENT_VERSION,
                Instant.now(),
                resolveTraceId(),
                correlationId,
                sagaId,
                PRODUCER,
                payload);

        outboxRepository.save(new OutboxEvent(
                UUID.randomUUID(), AGGREGATE_TYPE, aggregateId, eventType, EVENT_VERSION, serialize(envelope)));
    }

    private String serialize(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize event envelope for outbox: eventType=" + envelope.eventType(), e);
        }
    }

    /** Reads traceId from MDC (set by {@link CorrelationIdFilter}), falls back to a new UUID. */
    private String resolveTraceId() {
        String traceId = MDC.get(CorrelationIdFilter.TRACE_ID_MDC_KEY);
        return (traceId != null && !traceId.isBlank())
                ? traceId
                : UUID.randomUUID().toString();
    }
}
