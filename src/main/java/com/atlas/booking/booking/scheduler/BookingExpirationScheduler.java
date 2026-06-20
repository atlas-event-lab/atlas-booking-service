package com.atlas.booking.booking.scheduler;

import com.atlas.booking.booking.entity.Booking;
import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.repository.BookingRepository;
import com.atlas.booking.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Safety-net scheduler that drives stale {@code PENDING} Bookings to {@code EXPIRED}
 * (features/booking-expiration). Covers Bookings whose {@code BookingCreated} produced no
 * inventory outcome at all — the only state with no downstream owner to settle it.
 * <p>
 * Scope is {@code PENDING} <strong>only</strong> by design (feature.md addendum — "Timeout
 * Ownership"): while {@code INVENTORY_RESERVED}, Payment is charging the card, so timing that
 * step out from here would race the charge and risk an orphan charge. {@code INVENTORY_RESERVED
 * → EXPIRED} is owned exclusively by Payment's {@code PaymentTimedOut} event.
 * <p>
 * Idempotent and stateless (ARCH-010): the deadline derives from the persisted {@code updatedAt};
 * {@code fixedDelay} prevents a slow run from overlapping the next tick within an instance, and the
 * state-machine guard makes a second pass over an already-terminal Booking a no-op
 * (coding-standards §Spring Boot — "Scheduled jobs SHALL be idempotent").
 * <p>
 * Each Booking is expired in its own transaction inside {@link BookingService#expireBooking(java.util.UUID)};
 * a failure on one Booking does not abort the batch. The {@code BookingExpired} event is written to the
 * outbox and published by {@code OutboxRelay} (EVT-009/EVT-010).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final BookingExpirationProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${atlas.booking.expiration.scan-interval-ms:60000}")
    public void expireStaleBookings() {
        Instant now = clock.instant();
        // PENDING only — INVENTORY_RESERVED is settled by Payment's PaymentTimedOut (feature.md addendum).
        expireStaleInState(BookingStatus.PENDING, now.minus(properties.pendingTimeout()));
    }

    private void expireStaleInState(BookingStatus status, Instant cutoff) {
        List<Booking> stale =
                bookingRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(status, cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.info("Expiring {} stale {} booking(s) older than {}", stale.size(), status, cutoff);
        for (Booking booking : stale) {
            try {
                bookingService.expireBooking(booking.getBookingId());
            } catch (Exception e) {
                // Isolate per-Booking failures so the rest of the batch still proceeds.
                log.error("Failed to expire booking: bookingId={}", booking.getBookingId(), e);
            }
        }
    }
}
