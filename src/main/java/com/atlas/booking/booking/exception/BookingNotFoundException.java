package com.atlas.booking.booking.exception;

import java.util.UUID;

/** Thrown when a Booking cannot be found by its identifier. Maps to HTTP 404. */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(UUID bookingId) {
        super("Booking not found: " + bookingId);
    }
}
