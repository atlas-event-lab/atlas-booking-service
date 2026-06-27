package com.atlas.booking.booking.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Booking API response (booking.yaml BookingResponse).
 * Only public statuses are exposed; the mapper translates internal saga states before
 * constructing this DTO.
 */
public record BookingResponse(
        UUID bookingId,
        ApiBookingStatus status,
        MoneyResponse total,
        Instant createdAt
) {}
