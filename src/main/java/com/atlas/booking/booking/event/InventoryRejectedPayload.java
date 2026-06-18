package com.atlas.booking.booking.event;

import java.util.UUID;

/**
 * Payload of the InventoryRejected event consumed from Inventory Service.
 * Contract is pendiente; structure follows services/booking/events.md and feature.md.
 */
public record InventoryRejectedPayload(UUID bookingId, String reason) {}
