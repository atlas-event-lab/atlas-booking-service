package com.atlas.booking.booking.event;

import java.math.BigDecimal;

/**
 * Money value representation inside Kafka event payloads (booking-events.yaml Money schema).
 */
public record MoneyEvent(BigDecimal amount, String currency) {}
