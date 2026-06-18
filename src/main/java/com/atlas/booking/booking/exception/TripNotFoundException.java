package com.atlas.booking.booking.exception;

import java.util.UUID;

/** Thrown when the referenced Trip cannot be resolved via the Search Service. Maps to HTTP 422. */
public class TripNotFoundException extends RuntimeException {

    public TripNotFoundException(UUID tripId) {
        super("Trip not found: " + tripId);
    }
}
