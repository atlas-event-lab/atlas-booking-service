package com.atlas.booking.booking.exception;

/** Thrown when the recomputed Grand Total does not match the Trip total. Maps to HTTP 422. */
public class PricingMismatchException extends RuntimeException {

    public PricingMismatchException(String message) {
        super(message);
    }
}
