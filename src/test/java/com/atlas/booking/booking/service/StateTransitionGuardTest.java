package com.atlas.booking.booking.service;

import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.exception.InvalidStateTransitionException;
import com.atlas.booking.booking.exception.PrematureSagaEventException;
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
            "PENDING,           EXPIRED",
            "INVENTORY_RESERVED, CONFIRMED",
            "INVENTORY_RESERVED, FAILED",
            "INVENTORY_RESERVED, EXPIRED",
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
            "PENDING,            CANCELLING",
            "PENDING,            CANCELLED",
            "INVENTORY_RESERVED, PENDING",
            "INVENTORY_RESERVED, CANCELLING",
            "INVENTORY_RESERVED, CANCELLED",
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

    // ── Payment transitions: three-way classification (ADR-0007) ──────────────

    @ParameterizedTest(name = "PENDING → {0} (payment) is premature → retry")
    @CsvSource({
            "CONFIRMED",   // PAYMENT_SUCCEEDED
            "FAILED",      // PAYMENT_FAILED
            "EXPIRED"      // PAYMENT_TIMED_OUT
    })
    void paymentTransition_from_PENDING_is_premature(String to) {
        assertThatThrownBy(() ->
                StateTransitionGuard.assertPaymentTransition(
                        BookingStatus.PENDING, BookingStatus.valueOf(to)))
                .isInstanceOf(PrematureSagaEventException.class);
    }

    @ParameterizedTest(name = "INVENTORY_RESERVED → {0} (payment) is allowed")
    @CsvSource({
            "CONFIRMED",
            "FAILED",
            "EXPIRED"
    })
    void paymentTransition_from_INVENTORY_RESERVED_is_allowed(String to) {
        assertThatCode(() ->
                StateTransitionGuard.assertPaymentTransition(
                        BookingStatus.INVENTORY_RESERVED, BookingStatus.valueOf(to)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} → CONFIRMED (payment) from terminal is illegal")
    @CsvSource({
            "CONFIRMED",
            "FAILED",
            "EXPIRED",
            "CANCELLED"
    })
    void paymentTransition_from_terminal_is_illegal(String from) {
        assertThatThrownBy(() ->
                StateTransitionGuard.assertPaymentTransition(
                        BookingStatus.valueOf(from), BookingStatus.CONFIRMED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
