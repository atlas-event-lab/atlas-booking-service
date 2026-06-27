package com.atlas.booking.booking.exception;

/**
 * Thrown when catalog validation fails — e.g. a resource is WITHDRAWN or the
 * client-quoted price does not match the current catalog price. Maps to HTTP 422.
 */
public class CatalogValidationException extends RuntimeException {

    public CatalogValidationException(String message) {
        super(message);
    }
}
