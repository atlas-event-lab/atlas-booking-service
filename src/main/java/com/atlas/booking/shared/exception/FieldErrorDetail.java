package com.atlas.booking.shared.exception;

/** Per-field validation error as defined in the RFC 7807 ValidationProblemDetail contract (API-005). */
public record FieldErrorDetail(String field, String message) {}
