package com.atlas.booking.booking.service;

import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.exception.InvalidStateTransitionException;
import com.atlas.booking.booking.exception.PrematureSagaEventException;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces the Booking state machine (services/booking/state_machine.md).
 * Terminal states (CANCELLED, FAILED, EXPIRED) have no outgoing transitions.
 */
@Slf4j
public final class StateTransitionGuard {

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
            BookingStatus.PENDING,
                    Set.of(BookingStatus.INVENTORY_RESERVED, BookingStatus.FAILED, BookingStatus.EXPIRED),
            BookingStatus.INVENTORY_RESERVED,
                    Set.of(BookingStatus.CONFIRMED, BookingStatus.FAILED, BookingStatus.EXPIRED),
            BookingStatus.CONFIRMED, Set.of(BookingStatus.CANCELLING),
            BookingStatus.CANCELLING, Set.of(BookingStatus.CANCELLED));

    private StateTransitionGuard() {}

    /**
     * Asserts the transition from {@code from} to {@code to} is allowed.
     *
     * @throws InvalidStateTransitionException if the transition is forbidden.
     */
    public static void assertAllowed(BookingStatus from, BookingStatus to) {
        Set<BookingStatus> reachable = ALLOWED.getOrDefault(from, Set.of());
        if (!reachable.contains(to)) {
            log.error("Invalid State Transition from {} to {}", from.name(), to.name());
            throw new InvalidStateTransitionException(from, to);
        }
    }

    /**
     * Asserts a transition triggered by a <em>Payment outcome</em> event
     * ({@code PAYMENT_SUCCEEDED} / {@code PAYMENT_FAILED} / {@code PAYMENT_TIMED_OUT}), which is
     * only produced <em>after</em> the booking has reached {@code INVENTORY_RESERVED} (ADR-0007).
     * Classifies the requested transition three ways:
     * <ul>
     *   <li><b>Premature</b> — the booking is still in the legitimate earlier {@code PENDING}
     *       state: the causal predecessor ({@code inventory.reserved}) has not been processed yet,
     *       so the event arrived out of order. Throws {@link PrematureSagaEventException}
     *       (<em>retryable</em>) so it is re-tried rather than sent to the DLQ.</li>
     *   <li><b>Allowed</b> — from {@code INVENTORY_RESERVED} the normal transition applies.</li>
     *   <li><b>Illegal</b> — from a terminal or otherwise incompatible state; delegates to
     *       {@link #assertAllowed(BookingStatus, BookingStatus)} which throws
     *       {@link InvalidStateTransitionException} (non-retryable → DLQ, a genuine anomaly).</li>
     * </ul>
     */
    public static void assertPaymentTransition(BookingStatus from, BookingStatus to) {
        if (from == BookingStatus.PENDING) {
            log.warn(
                    "Premature payment Saga event: {} → {} before inventory.reserved; deferring (retry)",
                    from.name(),
                    to.name());
            throw new PrematureSagaEventException(from, to);
        }
        assertAllowed(from, to);
    }
}
