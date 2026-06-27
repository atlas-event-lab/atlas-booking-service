package com.atlas.booking.booking.event;

import java.util.List;
import java.util.UUID;

/**
 * Payload for the BookingCreated Kafka event (booking-events.yaml BookingCreatedPayload).
 *
 * {@code travelers} carries the traveler COUNT only — full personal data is persisted
 * by Booking Service and must not travel in events (data minimisation, booking-events.yaml).
 */
public record BookingCreatedPayload(
        UUID bookingId,
        UUID userId,
        List<BookingItemEvent> items,
        Integer travelers,
        MoneyEvent total
) {}
