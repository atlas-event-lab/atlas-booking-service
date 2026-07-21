package com.atlas.booking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A selected bookable item within a Booking (ADR-0010). Abstract base of a JPA {@code SINGLE_TABLE}
 * hierarchy: {@link FlightBookingItem} (no dates) and {@link HotelBookingItem} (carries the stay
 * range). One physical {@code booking_items} table discriminated by {@code type}; the hotel date
 * columns are nullable. Amounts are stored without currency; the parent Booking carries the shared
 * currency.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING, length = 20)
@Table(name = "booking_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public abstract class BookingItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID bookingItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    protected BookingItem(
            UUID bookingItemId, UUID resourceId, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
        this.bookingItemId = bookingItemId;
        this.resourceId = resourceId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    /** The item type; drives the resource family and event payload shape. */
    public abstract BookingItemType type();

    /** Called only by {@link Booking#addItem(BookingItem)}. */
    void setBooking(Booking booking) {
        this.booking = booking;
    }
}
