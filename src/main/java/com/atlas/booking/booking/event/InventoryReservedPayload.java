package com.atlas.booking.booking.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Payload of the InventoryReserved event consumed from Inventory Service.
 * Contract is pendiente; structure follows services/booking/events.md and feature.md.
 */
public record InventoryReservedPayload(@NotNull UUID bookingId, @Valid @NotNull List<ReservedItem> items) {}
