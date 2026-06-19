package com.atlas.booking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit record of every Booking status transition. */
@Entity
@Table(name = "booking_status_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BookingStatusHistory {

    @Id
    @Column(nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    /** Null only for the initial PENDING transition (no prior state). */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 50)
    private BookingStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 50)
    private BookingStatus newStatus;

    @CreatedDate
    @Column(name = "transitioned_at", nullable = false, updatable = false)
    private Instant transitionedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    public BookingStatusHistory(UUID id, BookingStatus previousStatus, BookingStatus newStatus) {
        this(id, previousStatus, newStatus, null);
    }

    public BookingStatusHistory(UUID id, BookingStatus previousStatus, BookingStatus newStatus, String reason) {
        this.id = id;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }

    /** Called only by {@link Booking#addStatusHistory(BookingStatusHistory)}. */
    void setBooking(Booking booking) {
        this.booking = booking;
    }
}
