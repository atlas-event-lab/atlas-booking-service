package com.atlas.booking.booking.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalized deadline for the Booking expiration safety-net job
 * (coding-standards §Spring Boot/§Configuration — no hardcoded values).
 *
 * @param pendingTimeout a PENDING Booking older than this is expirable.
 *
 * <p>The job expires {@code PENDING} only (feature.md addendum — "Timeout Ownership"), so this is
 * the single deadline it consumes. Per the cascading timeout budget it SHALL be the
 * <strong>outermost/longest</strong> deadline — ≥ the inventory reservation TTL + margin — so the
 * authoritative owner timeouts (payment, then inventory TTL) always fire first and this safety net
 * almost never triggers in normal operation.
 *
 * <p>The scan interval is read directly from {@code atlas.booking.expiration.scan-interval-ms}
 * by the scheduler's {@code @Scheduled(fixedDelayString=...)} (same approach as OutboxRelay).
 */
@ConfigurationProperties(prefix = "atlas.booking.expiration")
public record BookingExpirationProperties(
        Duration pendingTimeout
) {}
