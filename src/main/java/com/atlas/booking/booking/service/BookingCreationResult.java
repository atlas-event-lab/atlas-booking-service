package com.atlas.booking.booking.service;

import com.atlas.booking.booking.dto.BookingResponse;

/**
 * Wraps the outcome of {@link BookingService#createBooking} so the controller
 * can return 201 (new booking) or 200 (idempotent replay) without business logic.
 * Holds a mapped DTO — the service layer performs entity-to-DTO mapping before
 * returning (coding-standards §Layer Responsibilities).
 */
public record BookingCreationResult(BookingResponse booking, boolean isReplay) {}
