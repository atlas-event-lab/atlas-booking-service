package com.atlas.booking.booking.messaging;

import com.atlas.booking.booking.exception.BookingNotFoundException;
import com.atlas.booking.booking.exception.InvalidStateTransitionException;
import com.atlas.booking.booking.service.BookingService;
import com.atlas.booking.shared.messaging.EventTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for the Booking-side choreography Saga (Phase 6).
 * <p>
 * Listens on Inventory and Payment topics and delegates each transition to
 * {@link BookingService}. The service methods are {@code @Transactional} and
 * idempotent: a re-delivered event with the same {@code eventId} causes no second
 * state transition (EVT-005, EVT-008).
 * <p>
 * Retry strategy (retry-strategy.md): 4 total attempts (1 initial + 3 retries) with
 * non-blocking retry via Retry Topics. Delays: 5 s → 30 s → 120 s → DLQ.
 * Business-logic failures ({@link BookingNotFoundException},
 * {@link InvalidStateTransitionException}, {@link IllegalArgumentException}) go
 * straight to DLQ without retries (non-retryable per retry-strategy.md).
 * <p>
 * DLQ topic naming: {@code <original-topic>.dlq} (dlq-strategy.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final BookingService bookingService;

    // ── Inventory events ──────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            dltTopicSuffix = ".dlq",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class}
    )
    @KafkaListener(topics = EventTopics.INVENTORY_BOOKING_RESERVED,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryReserved(Map<String, Object> envelope) {
        UUID eventId   = extractEventId(envelope);
        UUID bookingId = extractBookingId(extractPayload(envelope));

        log.debug("Received InventoryReserved: eventId={}, bookingId={}", eventId, bookingId);
        bookingService.onInventoryReserved(eventId, bookingId);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            dltTopicSuffix = ".dlq",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class}
    )
    @KafkaListener(topics = EventTopics.INVENTORY_BOOKING_REJECTED,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryRejected(Map<String, Object> envelope) {
        UUID eventId   = extractEventId(envelope);
        UUID bookingId = extractBookingId(extractPayload(envelope));

        log.debug("Received InventoryRejected: eventId={}, bookingId={}", eventId, bookingId);
        bookingService.onInventoryRejected(eventId, bookingId);
    }

    // ── Payment events ────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            dltTopicSuffix = ".dlq",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class}
    )
    @KafkaListener(topics = EventTopics.PAYMENT_SUCCEEDED,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentSucceeded(Map<String, Object> envelope) {
        UUID eventId   = extractEventId(envelope);
        Map<String, Object> payload = extractPayload(envelope);
        UUID bookingId = extractBookingId(payload);
        UUID paymentId = extractPaymentId(payload);

        log.debug("Received PaymentSucceeded: eventId={}, bookingId={}, paymentId={}",
                eventId, bookingId, paymentId);
        bookingService.onPaymentSucceeded(eventId, bookingId, paymentId);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            dltTopicSuffix = ".dlq",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class}
    )
    @KafkaListener(topics = EventTopics.PAYMENT_FAILED,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentFailed(Map<String, Object> envelope) {
        UUID eventId   = extractEventId(envelope);
        UUID bookingId = extractBookingId(extractPayload(envelope));

        log.debug("Received PaymentFailed: eventId={}, bookingId={}", eventId, bookingId);
        bookingService.onPaymentFailed(eventId, bookingId);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            dltTopicSuffix = ".dlq",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class}
    )
    @KafkaListener(topics = EventTopics.PAYMENT_TIMED_OUT,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentTimedOut(Map<String, Object> envelope) {
        UUID eventId   = extractEventId(envelope);
        UUID bookingId = extractBookingId(extractPayload(envelope));

        log.debug("Received PaymentTimedOut: eventId={}, bookingId={}", eventId, bookingId);
        bookingService.onPaymentTimedOut(eventId, bookingId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID extractEventId(Map<String, Object> envelope) {
        Object raw = envelope.get("eventId");
        if (raw == null) {
            throw new IllegalArgumentException("Missing eventId in envelope");
        }
        return UUID.fromString(raw.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> envelope) {
        Object raw = envelope.get("payload");
        if (raw == null) {
            throw new IllegalArgumentException("Missing payload in envelope");
        }
        return (Map<String, Object>) raw;
    }

    private UUID extractBookingId(Map<String, Object> payload) {
        return extractUuidField(payload, "bookingId");
    }

    private UUID extractPaymentId(Map<String, Object> payload) {
        return extractUuidField(payload, "paymentId");
    }

    private UUID extractUuidField(Map<String, Object> payload, String field) {
        Object raw = payload.get(field);
        if (raw == null) {
            throw new IllegalArgumentException("Missing field '" + field + "' in payload");
        }
        return UUID.fromString(raw.toString());
    }
}
