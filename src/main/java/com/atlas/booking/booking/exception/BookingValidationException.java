package com.atlas.booking.booking.exception;

/**
 * Raised on invalid Create Booking input that field-level Bean Validation can't express (cross-field
 * rules such as hotel stay dates, ADR-0010). Mapped to 400 RFC7807 (API-004/API-005).
 */
public class BookingValidationException extends RuntimeException {

    public BookingValidationException(String message) {
        super(message);
    }
}
