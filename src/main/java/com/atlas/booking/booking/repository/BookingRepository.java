package com.atlas.booking.booking.repository;

import com.atlas.booking.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the Booking aggregate root. Accesses only local Booking entities (DB-004).
 */
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
}
