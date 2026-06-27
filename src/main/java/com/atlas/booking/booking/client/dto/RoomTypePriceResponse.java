package com.atlas.booking.booking.client.dto;

import java.util.UUID;

/** Room type price response from Hotel Service (hotel.yaml GET /hotels/{hotelId}/room-types/{roomTypeId}/price). */
public record RoomTypePriceResponse(UUID hotelId, UUID roomTypeId, MoneyDto pricePerNight, String status) {}
