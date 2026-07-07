package com.atlas.booking.booking.service;

import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.exception.InvalidStateTransitionException;

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
            BookingStatus.PENDING, Set.of(
                    BookingStatus.INVENTORY_RESERVED,
                    BookingStatus.FAILED,
                    BookingStatus.EXPIRED),
            BookingStatus.INVENTORY_RESERVED, Set.of(
                    BookingStatus.CONFIRMED,
                    BookingStatus.FAILED,
                    BookingStatus.EXPIRED),
            BookingStatus.CONFIRMED, Set.of(
                    BookingStatus.CANCELLING),
            BookingStatus.CANCELLING, Set.of(
                    BookingStatus.CANCELLED)
    );

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
}
