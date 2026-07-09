package com.atlas.booking.booking.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Booking item representation inside Kafka event payloads (booking-events.yaml BookingItem schema).
 * {@code checkIn} / {@code checkOut} are present for HOTEL items (the stay range Inventory reserves
 * per night, ADR-0010) and null for FLIGHT items.
 */
public record BookingItemEvent(
        String type,
        UUID resourceId,
        Integer quantity,
        BigDecimal amount,
        LocalDate checkIn,
        LocalDate checkOut
) {}
