package com.atlas.booking.booking.repository;

import com.atlas.booking.booking.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for the Transactional Outbox (EVT-009). Accesses only local entities (DB-004).
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims a batch of unpublished rows for this relay instance, oldest-first, taking a
     * row-level lock and skipping rows already locked by another replica's poll
     * ({@code FOR UPDATE SKIP LOCKED}). Each concurrent relay therefore claims a <em>disjoint</em>
     * batch, so no event is published twice in steady state (ADR-0013). It MUST run inside the
     * relay's transaction so the locks are held until the rows are marked PUBLISHED / FAILED.
     * Backed by the {@code (status, created_at)} index.
     */
    @Query(
            value =
                    """
            SELECT * FROM outbox
            WHERE status IN ('PENDING', 'FAILED')
            ORDER BY created_at
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    List<OutboxEvent> claimBatchForPublishing();
}
