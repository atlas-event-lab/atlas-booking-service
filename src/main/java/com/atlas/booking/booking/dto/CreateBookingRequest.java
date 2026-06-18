package com.atlas.booking.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Request body for POST /bookings (booking.yaml CreateBookingRequest).
 * The total is never client-supplied; it is recomputed from the Trip (feature.md).
 */
public record CreateBookingRequest(

        @NotNull
        UUID tripId,

        @NotNull
        @Size(min = 1)
        @Valid
        List<TravelerRequest> travelers,

        @NotNull
        @Size(min = 1)
        @Valid
        List<BookingItemSelectionRequest> items
) {}
