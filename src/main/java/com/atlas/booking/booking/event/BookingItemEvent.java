package com.atlas.booking.booking.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Booking item representation inside Kafka event payloads (booking-events.yaml BookingItem schema).
 */
public record BookingItemEvent(String type, UUID resourceId, Integer quantity, BigDecimal amount) {}
