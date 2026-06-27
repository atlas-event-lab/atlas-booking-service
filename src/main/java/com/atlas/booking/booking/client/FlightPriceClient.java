package com.atlas.booking.booking.client;

import com.atlas.booking.booking.client.dto.FlightPriceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Read-only REST client for the Flight Service price endpoint.
 * Used to validate flight pricing before booking creation (ARCH-003, ARCH-006).
 */
@FeignClient(
    name = "flight-service",
    url = "${clients.flight.base-url}"
)
public interface FlightPriceClient {

    @GetMapping("/api/v1/flights/{flightId}/price")
    FlightPriceResponse getFlightPrice(@PathVariable UUID flightId);
}
