package com.atlas.booking.booking.event;

import java.util.UUID;

/**
 * Payload for BookingConfirmed, BookingFailed and BookingExpired events
 * as defined in booking-events.yaml (BookingLifecyclePayload schema).
 */
public record BookingLifecyclePayload(UUID bookingId, UUID userId, String status) {}
