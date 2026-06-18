package com.atlas.booking.booking.controller;

import com.atlas.booking.booking.dto.BookingResponse;
import com.atlas.booking.booking.dto.CreateBookingRequest;
import com.atlas.booking.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for Booking endpoints (booking.yaml).
 * Contains no business logic; delegates entirely to {@link BookingService}.
 * Entity-to-DTO mapping is performed in the service layer (coding-standards §Layer Responsibilities).
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * POST /api/v1/bookings — creates a Booking (booking.yaml operationId: createBooking).
     * Returns 201 on creation or 200 on idempotent replay (same Idempotency-Key + same payload).
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid CreateBookingRequest request) {

        var result = bookingService.createBooking(idempotencyKey, request);
        HttpStatus status = result.isReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.booking());
    }

    /** GET /api/v1/bookings/{bookingId} — retrieves a Booking (booking.yaml operationId: getBooking). */
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(bookingService.getBooking(bookingId));
    }
}
