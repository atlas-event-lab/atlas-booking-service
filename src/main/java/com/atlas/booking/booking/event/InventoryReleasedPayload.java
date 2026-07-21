package com.atlas.booking.booking.event;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record InventoryReleasedPayload(@NotNull UUID bookingId, List<UUID> reservationIds) {}
