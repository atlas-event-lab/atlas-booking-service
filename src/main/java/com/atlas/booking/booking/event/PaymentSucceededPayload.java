package com.atlas.booking.booking.event;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload of the PaymentSucceeded event consumed from Payment Service.
 * Contract is pendiente; structure follows services/booking/events.md and feature.md.
 */
public record PaymentSucceededPayload(
    @NotNull
    UUID bookingId,

    @NotNull
    UUID paymentId
) {}
