package com.atlas.booking.booking.event;

import java.util.UUID;

/**
 * Payload of the InventoryReserved event consumed from Inventory Service.
 * Contract is pendiente; structure follows services/booking/events.md and feature.md.
 */
public record InventoryReservedPayload(UUID bookingId, UUID reservationId) {}
