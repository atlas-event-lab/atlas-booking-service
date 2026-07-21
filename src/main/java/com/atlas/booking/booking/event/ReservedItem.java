package com.atlas.booking.booking.event;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ReservedItem(
        @NotNull UUID reservationId,
        @NotNull ResourceType resourceType,
        @NotNull UUID resourceId,
        int quantity,
        BigDecimal amount) {}
