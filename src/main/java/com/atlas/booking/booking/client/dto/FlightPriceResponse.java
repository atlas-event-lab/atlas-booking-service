package com.atlas.booking.booking.client.dto;

import java.util.UUID;

/** Flight price response from Flight Service (flight.yaml GET /flights/{flightId}/price). */
public record FlightPriceResponse(UUID flightId, MoneyDto basePrice, String status) {}
