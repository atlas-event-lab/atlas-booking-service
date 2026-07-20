package com.atlas.booking.booking.messaging;

import com.atlas.booking.booking.event.EventValidator;
import com.atlas.booking.booking.event.InventoryRejectedPayload;
import com.atlas.booking.booking.event.InventoryReleasedPayload;
import com.atlas.booking.booking.event.InventoryReservedPayload;
import com.atlas.booking.booking.event.PaymentFailedPayload;
import com.atlas.booking.booking.event.PaymentSucceededPayload;
import com.atlas.booking.booking.event.PaymentTimedOutPayload;
import com.atlas.booking.booking.exception.BookingNotFoundException;
import com.atlas.booking.booking.exception.InvalidStateTransitionException;
import com.atlas.booking.booking.service.BookingService;
import com.atlas.booking.booking.event.EventEnvelope;
import com.atlas.booking.shared.messaging.EventTopics;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

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
 * Out-of-order Saga arrivals (ADR-0007) raise {@code PrematureSagaEventException}, which is
 * deliberately kept <b>out</b> of every {@code exclude} list so the event flows through the
 * retry ladder (its causal predecessor has virtually always been processed by the first retry)
 * instead of being discarded to the DLQ as a false anomaly.
 * <p>
 * DLQ topic naming: {@code <original-topic>.dlq} (dlq-strategy.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final BookingService bookingService;
    private final EventValidator eventValidator;

    // ── Inventory events ──────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            retryTopicSuffix = "-booking-retry",
            dltTopicSuffix = "-booking.dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class, ConstraintViolationException.class}
    )
    @KafkaListener(topics = EventTopics.INVENTORY_BOOKING_RESERVED,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryReserved(EventEnvelope<InventoryReservedPayload> envelope) {
        eventValidator.validate(envelope);
        UUID eventId   = envelope.eventId();
        UUID bookingId = envelope.payload().bookingId();

        log.info("Received InventoryReserved: eventId={}, bookingId={}", eventId, bookingId);
        bookingService.onInventoryReserved(eventId, bookingId);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class, ConstraintViolationException.class}
    )
    @KafkaListener(topics = EventTopics.INVENTORY_BOOKING_REJECTED,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryRejected(EventEnvelope<InventoryRejectedPayload> envelope) {
        eventValidator.validate(envelope);
        UUID eventId   = envelope.eventId();
        UUID bookingId = envelope.payload().bookingId();

        log.info("Received InventoryRejected: eventId={}, bookingId={}", eventId, bookingId);
        bookingService.onInventoryRejected(eventId, bookingId);
    }

    // ── Inventory: cancellation ───────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class, ConstraintViolationException.class}
    )
    @KafkaListener(topics = EventTopics.INVENTORY_BOOKING_RELEASED,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryReleased(EventEnvelope<InventoryReleasedPayload> envelope) {
        eventValidator.validate(envelope);
        UUID eventId   = envelope.eventId();
        UUID bookingId = envelope.payload().bookingId();

        log.info("Received InventoryReleased: eventId={}, bookingId={}", eventId, bookingId);
        bookingService.onInventoryReleased(eventId, bookingId);
    }

    // ── Payment events ────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class, ConstraintViolationException.class}
    )
    @KafkaListener(topics = EventTopics.PAYMENT_SUCCEEDED,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentSucceeded(EventEnvelope<PaymentSucceededPayload> envelope) {
        eventValidator.validate(envelope);
        UUID eventId   = envelope.eventId();
        PaymentSucceededPayload payload = envelope.payload();
        UUID bookingId = payload.bookingId();
        UUID paymentId = payload.paymentId();

        log.info("Received PaymentSucceeded: eventId={}, bookingId={}, paymentId={}",
                eventId, bookingId, paymentId);
        bookingService.onPaymentSucceeded(eventId, bookingId, paymentId);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class, ConstraintViolationException.class}
    )
    @KafkaListener(topics = EventTopics.PAYMENT_FAILED,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentFailed(EventEnvelope<PaymentFailedPayload> envelope) {
        eventValidator.validate(envelope);
        UUID eventId   = envelope.eventId();
        UUID bookingId = envelope.payload().bookingId();

        log.info("Received PaymentFailed: eventId={}, bookingId={}", eventId, bookingId);
        bookingService.onPaymentFailed(eventId, bookingId);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000L, multiplier = 6.0, maxDelay = 120_000L),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {BookingNotFoundException.class, InvalidStateTransitionException.class,
                       IllegalArgumentException.class, ConstraintViolationException.class}
    )
    @KafkaListener(topics = EventTopics.PAYMENT_TIMED_OUT,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentTimedOut(EventEnvelope<PaymentTimedOutPayload> envelope) {
        eventValidator.validate(envelope);
        UUID eventId   = envelope.eventId();
        UUID bookingId = envelope.payload().bookingId();

        log.info("Received PaymentTimedOut: eventId={}, bookingId={}", eventId, bookingId);
        bookingService.onPaymentTimedOut(eventId, bookingId);
    }
}
