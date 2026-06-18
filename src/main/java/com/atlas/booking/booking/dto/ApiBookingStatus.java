package com.atlas.booking.booking.dto;

/**
 * API-exposed booking statuses (booking.yaml BookingStatus).
 * Internal saga states (INVENTORY_RESERVED, PAYMENT_PENDING, CANCELLING) are never returned.
 */
public enum ApiBookingStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED,
    EXPIRED
}
