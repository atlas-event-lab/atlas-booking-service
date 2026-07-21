package com.atlas.booking.booking.event;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload of the InventoryRejected event consumed from Inventory Service.
 * Contract is pendiente; structure follows services/booking/events.md and feature.md.
 */
public record InventoryRejectedPayload(@NotNull UUID bookingId, String reason) {}
