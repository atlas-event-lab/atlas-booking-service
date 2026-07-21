package com.atlas.booking.booking.exception;

import com.atlas.booking.booking.entity.BookingStatus;
import java.util.UUID;

/**
 * Thrown when cancellation is requested for a Booking whose current state is not
 * cancellable (CANCELLING, CANCELLED, FAILED, EXPIRED). Maps to HTTP 409.
 */
public class BookingNotCancellableException extends RuntimeException {

    public BookingNotCancellableException(UUID bookingId, BookingStatus currentStatus) {
        super("Booking " + bookingId + " cannot be cancelled in state: " + currentStatus);
    }
}
