package com.atlas.booking.booking.client;

import com.atlas.booking.booking.client.dto.TripDetailResponse;

import java.util.Optional;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Read-only REST client for the Search Service.
 * Used exclusively to resolve a Trip and validate pricing (ARCH-003, ARCH-006).
 */
@FeignClient(
    name = "search-service",
    url = "${clients.search.base-url}"
)
public interface SearchClient {

    /**
     * Calls Search {@code GET /trips/{tripId}}.
     *
     * @return the TripDetail, or empty when the trip is not found (404).
     */
    @GetMapping("/api/v1/trips/{tripId}")
    Optional<TripDetailResponse> getTrip(@PathVariable UUID tripId);
}
