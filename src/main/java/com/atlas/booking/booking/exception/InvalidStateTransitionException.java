package com.atlas.booking.booking.exception;

import com.atlas.booking.booking.entity.BookingStatus;

/** Thrown when a requested Booking state transition is not allowed by the state machine. */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(BookingStatus from, BookingStatus to) {
        super("Invalid state transition: " + from + " → " + to);
    }
}
