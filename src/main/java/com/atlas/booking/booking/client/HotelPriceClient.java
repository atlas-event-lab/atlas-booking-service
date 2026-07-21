package com.atlas.booking.booking.client;

import com.atlas.booking.booking.client.dto.RoomTypePriceResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Read-only REST client for the Hotel Service room-type price endpoint.
 * Used to validate hotel pricing before booking creation (ARCH-003, ARCH-006).
 */
@FeignClient(name = "hotel-service", url = "${clients.hotel.base-url}")
public interface HotelPriceClient {

    @GetMapping("/api/v1/hotels/{hotelId}/room-types/{roomTypeId}/price")
    RoomTypePriceResponse getRoomTypePrice(@PathVariable UUID hotelId, @PathVariable UUID roomTypeId);
}
