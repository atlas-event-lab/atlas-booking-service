package com.atlas.booking.shared.messaging;

/**
 * Kafka topic name constants (topics.md, naming: domain.entity.event).
 * Topics prefixed booking.* are owned by Booking Service.
 * Topics prefixed inventory.* / payment.* are owned by the respective services;
 * constants are defined here for consumer reference only.
 * Topic names are immutable — never rename or reuse a topic.
 */
public final class EventTopics {

    // ── Booking Service produces ──────────────────────────────────────────────
    public static final String BOOKING_CREATED   = "booking.booking.created";
    public static final String BOOKING_CONFIRMED = "booking.booking.confirmed";
    public static final String BOOKING_CANCELLED = "booking.booking.cancelled";
    public static final String BOOKING_FAILED    = "booking.booking.failed";
    public static final String BOOKING_EXPIRED   = "booking.booking.expired";

    // ── Booking Service consumes (owned by Inventory Service) ─────────────────
    public static final String INVENTORY_BOOKING_RESERVED = "inventory.booking.reserved";
    public static final String INVENTORY_BOOKING_REJECTED = "inventory.booking.rejected";
    public static final String INVENTORY_BOOKING_RELEASED = "inventory.booking.released";

    // ── Booking Service consumes (owned by Payment Service) ───────────────────
    public static final String PAYMENT_SUCCEEDED  = "payment.payment.succeeded";
    public static final String PAYMENT_FAILED     = "payment.payment.failed";
    public static final String PAYMENT_TIMED_OUT  = "payment.payment.timed-out";

    private EventTopics() {}
}
