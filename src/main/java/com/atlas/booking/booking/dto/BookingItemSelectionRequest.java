package com.atlas.booking.booking.dto;

import com.atlas.booking.booking.client.dto.MoneyDto;
import com.atlas.booking.booking.entity.BookingItemType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Selected bookable item within a Create Booking request (booking.yaml BookingItemSelection).
 * {@code checkIn} / {@code checkOut} are required for HOTEL items (the stay range, ADR-0010) and
 * omitted for FLIGHT items; cross-field validation lives in the service.
 */
public record BookingItemSelectionRequest(
        @NotNull BookingItemType type,
        @NotNull UUID resourceId,
        UUID hotelId,
        @NotNull @Min(1) Integer quantity,
        @NotNull MoneyDto unitPrice,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {}
