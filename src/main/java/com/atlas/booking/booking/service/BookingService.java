package com.atlas.booking.booking.service;

import com.atlas.booking.booking.dto.BookingResponse;
import com.atlas.booking.booking.dto.CancelBookingRequest;
import com.atlas.booking.booking.dto.CreateBookingRequest;

import java.util.UUID;

/**
 * Booking Service contract. Business logic and saga coordination are in the implementation.
 * The controller delegates here and maps the returned entity to a DTO via BookingMapper.
 */
public interface BookingService {

    /**
     * Creates a new Booking for the authenticated user, or returns the original Booking
     * when the Idempotency-Key was already used with the same payload (EVT-008).
     * UserId is extracted from the JWT inside the implementation (SEC-004).
     *
     * @return a result containing the Booking and whether it was a replay.
     */
    BookingCreationResult createBooking(String idempotencyKey, CreateBookingRequest request);

    /**
     * Returns the mapped {@link BookingResponse} or throws
     * {@link com.atlas.booking.booking.exception.BookingNotFoundException}.
     * Entity-to-DTO mapping is performed inside the service (coding-standards §Layer Responsibilities).
     */
    BookingResponse getBooking(UUID bookingId);

    // ── Cancellation ─────────────────────────────────────────────────────────

    /**
     * Cancels a Booking for the authenticated user, or returns the original result when the
     * Idempotency-Key was already used for the same Booking (EVT-008).
     * UserId is extracted from the JWT inside the implementation (SEC-004).
     *
     * @return the Booking in its resulting state (CANCELLED immediately, or CONFIRMED while
     *         CANCELLING is in progress for a previously CONFIRMED Booking).
     */
    BookingResponse cancelBooking(String idempotencyKey, UUID bookingId, CancelBookingRequest request);

    // ── Saga choreography handlers ────────────────────────────────────────────

    /**
     * Transitions Booking from PENDING → INVENTORY_RESERVED.
     * Idempotent: re-delivered events with the same {@code eventId} are silently ignored (EVT-005).
     */
    void onInventoryReserved(UUID eventId, UUID bookingId);

    /**
     * Transitions Booking from PENDING → FAILED and publishes BookingFailed.
     * Idempotent: re-delivered events are silently ignored (EVT-005).
     */
    void onInventoryRejected(UUID eventId, UUID bookingId);

    /**
     * Transitions Booking from INVENTORY_RESERVED → CONFIRMED and publishes BookingConfirmed.
     * Also records the {@code paymentId} on the Booking.
     * Idempotent: re-delivered events are silently ignored (EVT-005).
     */
    void onPaymentSucceeded(UUID eventId, UUID bookingId, UUID paymentId);

    /**
     * Transitions Booking from INVENTORY_RESERVED → FAILED and publishes BookingFailed.
     * Idempotent: re-delivered events are silently ignored (EVT-005).
     */
    void onPaymentFailed(UUID eventId, UUID bookingId);

    /**
     * Transitions Booking from INVENTORY_RESERVED → EXPIRED and publishes BookingExpired.
     * Idempotent: re-delivered events are silently ignored (EVT-005).
     */
    void onPaymentTimedOut(UUID eventId, UUID bookingId);

    /**
     * Transitions Booking from CANCELLING → CANCELLED on InventoryReleased.
     * A Booking already CANCELLED (pre-confirmation cancellation path) is a no-op (EVT-005).
     * Idempotent: re-delivered events with the same {@code eventId} cause no second transition.
     */
    void onInventoryReleased(UUID eventId, UUID bookingId);

    // ── Expiration (safety-net scheduler) ─────────────────────────────────────

    /**
     * Expires a single stale {@code PENDING} Booking ({@code PENDING → EXPIRED}) and publishes
     * {@code BookingExpired} to the outbox (EVT-009). Runs in its own transaction (ARCH-007).
     * <p>
     * Scope is {@code PENDING} only (feature.md addendum — "Timeout Ownership"):
     * {@code INVENTORY_RESERVED → EXPIRED} is owned by Payment's {@code PaymentTimedOut}, never by
     * the scheduler. A Booking that is not {@code PENDING} (a concurrent Saga event already won) is
     * a no-op.
     */
    void expireBooking(UUID bookingId);
}
