package com.atlas.booking.booking.entity;

/**
 * All valid Booking states, including internal saga states not exposed through the API.
 * See services/booking/state_machine.md for valid transitions.
 */
public enum BookingStatus {

    /** Booking created; inventory reservation pending. Initial state. */
    PENDING,

    /** Inventory reserved; payment not yet processed. Internal saga state. */
    INVENTORY_RESERVED,

    /** Reserved for future asynchronous payment flows. Not used in MVP. */
    PAYMENT_PENDING,

    /** Inventory reserved and payment approved. Booking completed. */
    CONFIRMED,

    /** User requested cancellation; compensation in progress. Internal saga state. */
    CANCELLING,

    /** Booking cancelled by user. Terminal state. */
    CANCELLED,

    /** Saga failed; compensation completed. Terminal state. */
    FAILED,

    /** Booking expired due to payment timeout. Terminal state. */
    EXPIRED
}
