package com.atlas.booking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A booked hotel room type over a stay range (ADR-0010). Records {@code checkIn} / {@code checkOut}
 * (the stay occupies {@code [checkIn, checkOut)}) so the dates reach Inventory on {@code BookingCreated}.
 * Persisted in the shared {@code booking_items} table with discriminator {@code HOTEL}.
 * Subtotal = {@code pricePerNight × nights × rooms}.
 */
@Entity
@DiscriminatorValue("HOTEL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HotelBookingItem extends BookingItem {

    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    public HotelBookingItem(
            UUID bookingItemId,
            UUID resourceId,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal,
            LocalDate checkIn,
            LocalDate checkOut) {
        super(bookingItemId, resourceId, quantity, unitPrice, subtotal);
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    @Override
    public BookingItemType type() {
        return BookingItemType.HOTEL;
    }

    /** Number of nights in the stay: {@code checkOut − checkIn} (≥ 1). */
    public int nights() {
        return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
    }
}
