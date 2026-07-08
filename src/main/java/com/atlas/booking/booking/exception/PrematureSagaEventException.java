package com.atlas.booking.booking.exception;

import com.atlas.booking.booking.entity.BookingStatus;

/**
 * Thrown when a Saga event arrives <em>before its causal predecessor</em> and the target
 * transition is not yet reachable from a <em>legitimate earlier</em> Saga state (ADR-0007).
 * <p>
 * Unlike {@link InvalidStateTransitionException}, this is a <strong>transient, retryable</strong>
 * condition: the predecessor event (e.g. {@code inventory.reserved}) has virtually always been
 * processed by the first retry, at which point the transition succeeds. It is therefore kept
 * <strong>out</strong> of the consumer's {@code @RetryableTopic(exclude = …)} list so the event
 * flows through the non-blocking retry ladder instead of going straight to the DLQ
 * (retry-strategy.md — "Retries SHALL preserve ordering whenever possible").
 */
public class PrematureSagaEventException extends RuntimeException {

    public PrematureSagaEventException(BookingStatus from, BookingStatus to) {
        super("Premature Saga event: transition " + from + " → " + to
                + " requires a predecessor event not yet processed (retryable)");
    }
}
