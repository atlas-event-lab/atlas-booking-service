package com.atlas.booking.booking.service;

import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateTransitionGuardTest {

    // ── Valid transitions ────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "PENDING,           INVENTORY_RESERVED",
            "PENDING,           FAILED",
            "PENDING,           CANCELLED",
            "INVENTORY_RESERVED, CONFIRMED",
            "INVENTORY_RESERVED, FAILED",
            "INVENTORY_RESERVED, EXPIRED",
            "INVENTORY_RESERVED, CANCELLED",
            "CONFIRMED,         CANCELLING",
            "CANCELLING,        CANCELLED"
    })
    void allowedTransitions_do_not_throw(String from, String to) {
        assertThatCode(() ->
                StateTransitionGuard.assertAllowed(
                        BookingStatus.valueOf(from),
                        BookingStatus.valueOf(to)))
                .doesNotThrowAnyException();
    }

    // ── Invalid transitions ──────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1} is forbidden")
    @CsvSource({
            "PENDING,            CONFIRMED",
            "PENDING,            EXPIRED",
            "PENDING,            CANCELLING",
            "INVENTORY_RESERVED, PENDING",
            "INVENTORY_RESERVED, CANCELLING",
            "CONFIRMED,          FAILED",
            "CONFIRMED,          EXPIRED",
            "CONFIRMED,          CANCELLED",
            "CANCELLING,         PENDING",
            "CANCELLING,         CONFIRMED"
    })
    void forbiddenTransitions_throw_InvalidStateTransitionException(String from, String to) {
        assertThatThrownBy(() ->
                StateTransitionGuard.assertAllowed(
                        BookingStatus.valueOf(from),
                        BookingStatus.valueOf(to)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void terminal_FAILED_has_no_outgoing_transitions() {
        for (BookingStatus target : BookingStatus.values()) {
            assertThatThrownBy(() ->
                    StateTransitionGuard.assertAllowed(BookingStatus.FAILED, target))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Test
    void terminal_EXPIRED_has_no_outgoing_transitions() {
        for (BookingStatus target : BookingStatus.values()) {
            assertThatThrownBy(() ->
                    StateTransitionGuard.assertAllowed(BookingStatus.EXPIRED, target))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Test
    void terminal_CANCELLED_has_no_outgoing_transitions() {
        for (BookingStatus target : BookingStatus.values()) {
            assertThatThrownBy(() ->
                    StateTransitionGuard.assertAllowed(BookingStatus.CANCELLED, target))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }
}
