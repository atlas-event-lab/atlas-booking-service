package com.atlas.booking.booking.repository;

import com.atlas.booking.booking.entity.ConsumedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the consumed-event idempotency store (EVT-005, EVT-008).
 * Accesses only local entities (DB-004).
 */
public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, UUID> {}
