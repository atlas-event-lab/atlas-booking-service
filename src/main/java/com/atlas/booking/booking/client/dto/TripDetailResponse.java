package com.atlas.booking.booking.client.dto;

import java.util.List;
import java.util.UUID;

/** Trip read model returned by Search Service (search.yaml TripDetail). */
public record TripDetailResponse(UUID tripId, List<TripItemResponse> items, MoneyDto total) {}
