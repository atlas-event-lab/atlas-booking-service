package com.atlas.booking.booking.dto;

import com.atlas.booking.booking.client.dto.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for POST /bookings (booking.yaml CreateBookingRequest).
 * The total is never client-supplied; it is recomputed server-side from validated unit prices.
 */
public record CreateBookingRequest(

        @NotNull
        @Size(min = 1)
        @Valid
        List<TravelerRequest> travelers,

        @NotNull
        @Size(min = 1)
        @Valid
        List<BookingItemSelectionRequest> items,

        @NotNull
        MoneyDto total
) {}
