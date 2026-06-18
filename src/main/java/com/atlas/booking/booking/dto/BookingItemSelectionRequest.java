package com.atlas.booking.booking.dto;

import com.atlas.booking.booking.entity.BookingItemType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Selected bookable item within a Create Booking request (booking.yaml BookingItemSelection). */
public record BookingItemSelectionRequest(

        @NotNull
        BookingItemType type,

        @NotNull
        UUID resourceId,

        @NotNull
        @Min(1)
        Integer quantity
) {}
