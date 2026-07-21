package com.atlas.booking.booking.repository;

import com.atlas.booking.booking.entity.Booking;
import com.atlas.booking.booking.entity.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the Booking aggregate root. Accesses only local Booking entities (DB-004).
 */
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findByCancellationIdempotencyKey(String cancellationIdempotencyKey);

    /**
     * Reads a batch of stale Bookings in a given state whose last change ({@code updatedAt})
     * predates the supplied cutoff, oldest-first. Used by the expiration safety-net job;
     * backed by the {@code (status, updated_at)} index. Batched at 100 like the outbox relay.
     */
    List<Booking> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(BookingStatus status, Instant cutoff);
}
