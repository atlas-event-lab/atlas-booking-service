package com.atlas.booking.booking.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A booked flight (ADR-0010). Carries no stay dates. Persisted in the shared {@code booking_items}
 * table with discriminator {@code FLIGHT}. Subtotal = {@code unitPrice × quantity}.
 */
@Entity
@DiscriminatorValue("FLIGHT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlightBookingItem extends BookingItem {

    public FlightBookingItem(
            UUID bookingItemId, UUID resourceId, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
        super(bookingItemId, resourceId, quantity, unitPrice, subtotal);
    }

    @Override
    public BookingItemType type() {
        return BookingItemType.FLIGHT;
    }
}
