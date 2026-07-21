package com.atlas.booking.booking.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlas.booking.booking.dto.ApiBookingStatus;
import com.atlas.booking.booking.dto.BookingResponse;
import com.atlas.booking.booking.entity.Booking;
import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.support.BookingTestData;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BookingMapperImpl.class)
class BookingMapperTest {

    @Autowired
    BookingMapper bookingMapper;

    // ── Status projection ────────────────────────────────────────────────────

    @ParameterizedTest(name = "internal {0} maps to API {1}")
    @CsvSource({
        "PENDING,             PENDING",
        "INVENTORY_RESERVED,  PENDING",
        "PAYMENT_PENDING,     PENDING",
        "CONFIRMED,           CONFIRMED",
        "CANCELLING,          CONFIRMED",
        "CANCELLED,           CANCELLED",
        "FAILED,              FAILED",
        "EXPIRED,             EXPIRED"
    })
    void status_projection_maps_internal_to_api_status(String internal, String expected) {
        ApiBookingStatus result = bookingMapper.toApiStatus(BookingStatus.valueOf(internal));
        assertThat(result).isEqualTo(ApiBookingStatus.valueOf(expected));
    }

    // ── Full response mapping ────────────────────────────────────────────────

    @Test
    void toResponse_maps_all_fields() {
        Booking booking = BookingTestData.aBooking();
        Instant expectedCreatedAt = Instant.parse("2026-06-17T10:00:00Z");
        ReflectionTestUtils.setField(booking, "createdAt", expectedCreatedAt);

        BookingResponse response = bookingMapper.toResponse(booking);

        assertThat(response.bookingId()).isEqualTo(BookingTestData.BOOKING_ID);
        assertThat(response.status()).isEqualTo(ApiBookingStatus.PENDING);
        assertThat(response.total().amount()).isEqualByComparingTo(BookingTestData.TOTAL_AMOUNT);
        assertThat(response.total().currency()).isEqualTo(BookingTestData.CURRENCY);
        assertThat(response.createdAt()).isEqualTo(expectedCreatedAt);
    }
}
