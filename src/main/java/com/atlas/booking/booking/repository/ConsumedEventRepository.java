package com.atlas.booking.booking.repository;

import com.atlas.booking.booking.entity.ConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for the consumed-event idempotency store (EVT-005, EVT-008).
 * Accesses only local entities (DB-004).
 */
public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, UUID> {}
