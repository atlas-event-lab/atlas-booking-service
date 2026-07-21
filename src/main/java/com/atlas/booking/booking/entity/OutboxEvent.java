package com.atlas.booking.booking.entity;

import com.atlas.booking.shared.messaging.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Transactional Outbox row (EVT-009). The full event envelope is written here in the
 * same transaction as the booking state change; {@code OutboxRelay} publishes PENDING /
 * FAILED rows to Kafka and marks them PUBLISHED.
 */
@Entity
@Table(name = "outbox")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private EventType eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    public OutboxEvent(
            UUID id, String aggregateType, UUID aggregateId, EventType eventType, int eventVersion, String payload) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
    }

    /** Marks the row as published after a successful Kafka send. */
    public void markPublished(Instant when) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = when;
        this.attempts += 1;
    }

    /** Marks the row as failed so the relay retries it on a later poll. */
    public void markFailed() {
        this.status = OutboxStatus.FAILED;
        this.attempts += 1;
    }
}
