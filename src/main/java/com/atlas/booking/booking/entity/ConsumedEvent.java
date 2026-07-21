package com.atlas.booking.booking.entity;

import com.atlas.booking.shared.messaging.ConsumerEventType;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Records Kafka event IDs that have been successfully processed.
 * Guards against duplicate state transitions when an event is re-delivered (EVT-005, EVT-008).
 */
@Entity
@Table(name = "consumed_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumedEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private ConsumerEventType eventType;

    @CreatedDate
    @Column(name = "consumed_at", nullable = false, updatable = false)
    private Instant consumedAt;

    public ConsumedEvent(UUID eventId, ConsumerEventType eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }
}
