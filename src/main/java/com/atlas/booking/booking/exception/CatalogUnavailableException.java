package com.atlas.booking.booking.exception;

/**
 * Thrown when an upstream catalog service (Flight or Hotel) is unreachable.
 * Maps to HTTP 503 Service Unavailable.
 */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
