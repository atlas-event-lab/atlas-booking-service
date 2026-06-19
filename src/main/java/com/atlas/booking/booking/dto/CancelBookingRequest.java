package com.atlas.booking.booking.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional cancellation payload (booking.yaml CancelBookingRequest).
 * UserId is NEVER taken from the body — it is extracted from the JWT (SEC-004).
 */
public record CancelBookingRequest(
        @Size(max = 500) String reason
) {}
