package com.atlas.booking.booking.entity;

/** Lifecycle of an {@link OutboxEvent} row (coding-standards §Outbox & Event Publishing). */
public enum OutboxStatus {

    /** Written in the booking transaction, not yet published to Kafka. */
    PENDING,

    /** Successfully published to Kafka by the relay. */
    PUBLISHED,

    /** A publish attempt failed; the relay retries it on a later poll. */
    FAILED
}
