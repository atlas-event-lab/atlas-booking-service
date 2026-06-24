package com.atlas.booking.booking.client.dto;

import java.util.UUID;

/** One bookable item within a TripDetail response (search.yaml TripItem). */
public record TripItemResponse(
    String type,
    UUID resourceId,
    MoneyDto unitPrice,
    Integer quantity,
    MoneyDto lineTotal
) {}
