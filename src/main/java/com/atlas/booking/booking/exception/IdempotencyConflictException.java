package com.atlas.booking.booking.exception;

/** Thrown when an Idempotency-Key is reused with a different request payload. Maps to HTTP 409. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency-Key already used with a different payload: " + idempotencyKey);
    }
}
