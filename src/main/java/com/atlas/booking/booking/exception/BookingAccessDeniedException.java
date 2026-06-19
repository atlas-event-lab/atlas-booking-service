package com.atlas.booking.booking.exception;

import java.util.UUID;

/** Thrown when the authenticated user is not the owner of the Booking. Maps to HTTP 403. */
public class BookingAccessDeniedException extends RuntimeException {

    public BookingAccessDeniedException(UUID bookingId) {
        super("Access denied to Booking: " + bookingId);
    }
}
