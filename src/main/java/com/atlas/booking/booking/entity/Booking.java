package com.atlas.booking.booking.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root for the Booking domain.
 * Owns the lifecycle of all Booking Items, Travelers and status history (domain/booking.md).
 */
@Entity
@Table(name = "bookings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Booking {

    @Id
    @Column(name = "booking_id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID bookingId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private BookingStatus status;

    @Setter
    @Column(name = "payment_id")
    private UUID paymentId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",   column = @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "currency",     nullable = false, length = 3))
    })
    private Money total;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 36)
    private String correlationId;

    @Column(name = "saga_id", nullable = false, updatable = false)
    private UUID sagaId;

    @Column(name = "idempotency_key", unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Setter
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Setter
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BookingItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Traveler> travelers = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BookingStatusHistory> statusHistory = new ArrayList<>();

    public Booking(UUID bookingId, UUID userId, UUID tripId, BookingStatus status,
                   Money total, String correlationId, UUID sagaId,
                   String idempotencyKey, String requestHash) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.tripId = tripId;
        this.status = status;
        this.total = total;
        this.correlationId = correlationId;
        this.sagaId = sagaId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public void addItem(BookingItem item) {
        items.add(item);
        item.setBooking(this);
    }

    public void addTraveler(Traveler traveler) {
        travelers.add(traveler);
        traveler.setBooking(this);
    }

    public void addStatusHistory(BookingStatusHistory entry) {
        statusHistory.add(entry);
        entry.setBooking(this);
    }

    public List<BookingItem> getItems()                  { return Collections.unmodifiableList(items); }
    public List<Traveler> getTravelers()                 { return Collections.unmodifiableList(travelers); }
    public List<BookingStatusHistory> getStatusHistory() { return Collections.unmodifiableList(statusHistory); }
}
