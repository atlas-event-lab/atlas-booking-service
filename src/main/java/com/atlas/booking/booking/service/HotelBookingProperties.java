package com.atlas.booking.booking.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized rules for hotel booking items (ADR-0010; no hardcoded values,
 * coding-standards §Configuration).
 *
 * @param maxStayNights the maximum {@code nights = checkOut − checkIn} accepted on a hotel item;
 *                      longer stays are rejected 400 (recommended 30, must match Search).
 */
@ConfigurationProperties(prefix = "atlas.booking.hotel")
public record HotelBookingProperties(
        int maxStayNights
) {}
