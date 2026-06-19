package com.atlas.booking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A selected bookable item within a Booking (FLIGHT or HOTEL).
 * Amounts are stored without currency; the parent Booking carries the shared currency.
 */
@Entity
@Table(name = "booking_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class BookingItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID bookingItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingItemType type;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    public BookingItem(UUID bookingItemId, BookingItemType type, UUID resourceId,
                       Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
        this.bookingItemId = bookingItemId;
        this.type = type;
        this.resourceId = resourceId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    /** Called only by {@link Booking#addItem(BookingItem)}. */
    void setBooking(Booking booking) {
        this.booking = booking;
    }
}
