package com.atlas.booking.shared.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Common Atlas event envelope (message-envelope.md).
 * Every Kafka message MUST use this structure; business payloads go inside {@code payload}.
 * Metadata SHALL never appear inside the payload.
 *
 * @param <T> the business payload type.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String traceId,
        String correlationId,
        String sagaId,
        String producer,
        T payload
) {}
