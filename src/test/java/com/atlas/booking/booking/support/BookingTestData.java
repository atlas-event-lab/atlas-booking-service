package com.atlas.booking.booking.support;

import com.atlas.booking.booking.client.dto.FlightPriceResponse;
import com.atlas.booking.booking.client.dto.MoneyDto;
import com.atlas.booking.booking.client.dto.RoomTypePriceResponse;
import com.atlas.booking.booking.dto.ApiBookingStatus;
import com.atlas.booking.booking.dto.BookingItemSelectionRequest;
import com.atlas.booking.booking.dto.BookingResponse;
import com.atlas.booking.booking.dto.CancelBookingRequest;
import com.atlas.booking.booking.dto.CreateBookingRequest;
import com.atlas.booking.booking.dto.MoneyResponse;
import com.atlas.booking.booking.dto.TravelerRequest;
import com.atlas.booking.booking.entity.Booking;
import com.atlas.booking.booking.entity.BookingItemType;
import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.entity.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BookingTestData {

    public static final UUID BOOKING_ID      = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID USER_ID         = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID OTHER_USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final UUID RESOURCE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000004");
    public static final UUID PAYMENT_ID      = UUID.fromString("00000000-0000-0000-0000-000000000005");
    public static final UUID EVENT_ID        = UUID.fromString("00000000-0000-0000-0000-000000000006");
    public static final UUID SAGA_ID         = UUID.fromString("00000000-0000-0000-0000-000000000099");
    public static final UUID FLIGHT_ID       = UUID.fromString("00000000-0000-0000-0000-000000000020");
    public static final UUID HOTEL_ID        = UUID.fromString("00000000-0000-0000-0000-000000000021");
    public static final UUID ROOM_TYPE_ID    = UUID.fromString("00000000-0000-0000-0000-000000000022");
    public static final String CORRELATION_ID              = "test-correlation-id";
    public static final String IDEMPOTENCY_KEY             = "test-idempotency-key";
    public static final String CANCELLATION_IDEMPOTENCY_KEY = "test-cancellation-idempotency-key";
    public static final String CANCELLATION_REASON         = "Change of plans";
    public static final BigDecimal TOTAL_AMOUNT       = new BigDecimal("500.00");
    public static final BigDecimal FLIGHT_UNIT_PRICE  = new BigDecimal("350.00");
    public static final BigDecimal HOTEL_UNIT_PRICE   = new BigDecimal("150.00");
    public static final String CURRENCY          = "USD";

    private BookingTestData() {}

    public static TravelerRequest aTraveler() {
        return new TravelerRequest(
                "John", "Doe",
                LocalDate.of(1990, 1, 1),
                "PE", "PASSPORT", "A12345678",
                "john.doe@example.com", "+51999000001");
    }

    public static BookingItemSelectionRequest aFlightItem() {
        return new BookingItemSelectionRequest(
                BookingItemType.FLIGHT, FLIGHT_ID, null, 1,
                new MoneyDto(FLIGHT_UNIT_PRICE, CURRENCY));
    }

    public static BookingItemSelectionRequest aHotelItem() {
        return new BookingItemSelectionRequest(
                BookingItemType.HOTEL, ROOM_TYPE_ID, HOTEL_ID, 1,
                new MoneyDto(HOTEL_UNIT_PRICE, CURRENCY));
    }

    public static CreateBookingRequest aCreateBookingRequest() {
        return new CreateBookingRequest(
                List.of(aTraveler()),
                List.of(aFlightItem(), aHotelItem()));
    }

    public static FlightPriceResponse aFlightPriceResponse() {
        return new FlightPriceResponse(
                FLIGHT_ID,
                new MoneyDto(FLIGHT_UNIT_PRICE, CURRENCY),
                "ACTIVE");
    }

    public static RoomTypePriceResponse aRoomTypePriceResponse() {
        return new RoomTypePriceResponse(
                HOTEL_ID,
                ROOM_TYPE_ID,
                new MoneyDto(HOTEL_UNIT_PRICE, CURRENCY),
                "ACTIVE");
    }

    public static Booking aBooking() {
        return new Booking(
                BOOKING_ID, USER_ID,
                BookingStatus.PENDING,
                new Money(TOTAL_AMOUNT, CURRENCY),
                CORRELATION_ID, SAGA_ID,
                IDEMPOTENCY_KEY, "some-hash");
    }

    public static Booking aBookingWithStatus(BookingStatus status) {
        return new Booking(
                BOOKING_ID, USER_ID,
                status,
                new Money(TOTAL_AMOUNT, CURRENCY),
                CORRELATION_ID, SAGA_ID,
                IDEMPOTENCY_KEY, "some-hash");
    }

    public static BookingResponse aBookingResponse() {
        return new BookingResponse(
                BOOKING_ID,
                ApiBookingStatus.PENDING,
                new MoneyResponse(TOTAL_AMOUNT, CURRENCY),
                Instant.parse("2026-06-17T00:00:00Z"));
    }

    public static BookingResponse aBookingResponseWithStatus(ApiBookingStatus status) {
        return new BookingResponse(
                BOOKING_ID, status,
                new MoneyResponse(TOTAL_AMOUNT, CURRENCY),
                Instant.parse("2026-06-17T00:00:00Z"));
    }

    public static CancelBookingRequest aCancelBookingRequest() {
        return new CancelBookingRequest(CANCELLATION_REASON);
    }

    public static Booking aBookingOwnedByOtherUser(BookingStatus status) {
        return new Booking(
                BOOKING_ID, OTHER_USER_ID,
                status,
                new Money(TOTAL_AMOUNT, CURRENCY),
                CORRELATION_ID, SAGA_ID,
                IDEMPOTENCY_KEY, "some-hash");
    }
}
