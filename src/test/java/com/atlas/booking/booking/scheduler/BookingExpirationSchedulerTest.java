package com.atlas.booking.booking.scheduler;

import com.atlas.booking.booking.entity.Booking;
import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.repository.BookingRepository;
import com.atlas.booking.booking.service.BookingService;
import com.atlas.booking.booking.support.BookingTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExpirationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-06-19T12:00:00Z");
    private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(30);

    @Mock BookingRepository bookingRepository;
    @Mock BookingService    bookingService;

    private BookingExpirationScheduler newScheduler() {
        var properties = new BookingExpirationProperties(PENDING_TIMEOUT);
        return new BookingExpirationScheduler(
                bookingRepository, bookingService, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void expireStaleBookings_queries_PENDING_only_with_its_cutoff_and_expires_each() {
        Booking pending = BookingTestData.aBookingWithStatus(BookingStatus.PENDING);
        when(bookingRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(BookingStatus.PENDING), eq(NOW.minus(PENDING_TIMEOUT))))
                .thenReturn(List.of(pending));

        newScheduler().expireStaleBookings();

        verify(bookingService).expireBooking(BookingTestData.BOOKING_ID);
        // INVENTORY_RESERVED is owned by PaymentTimedOut — the job must never scan it.
        verify(bookingRepository, never()).findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(BookingStatus.INVENTORY_RESERVED), any());
    }

    @Test
    void expireStaleBookings_noStaleBookings_doesNothing() {
        when(bookingRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(BookingStatus.PENDING), eq(NOW.minus(PENDING_TIMEOUT))))
                .thenReturn(List.of());

        newScheduler().expireStaleBookings();

        verify(bookingService, never()).expireBooking(any());
    }

    @Test
    void expireStaleBookings_failureOnOneBooking_continuesBatch() {
        Booking b1 = BookingTestData.aBookingWithStatus(BookingStatus.PENDING);
        Booking b2 = BookingTestData.aBookingWithStatus(BookingStatus.PENDING);
        when(bookingRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(BookingStatus.PENDING), eq(NOW.minus(PENDING_TIMEOUT))))
                .thenReturn(List.of(b1, b2));
        doThrow(new RuntimeException("boom")).doNothing()
                .when(bookingService).expireBooking(BookingTestData.BOOKING_ID);

        newScheduler().expireStaleBookings();

        // Both Bookings are attempted even though the first threw.
        verify(bookingService, times(2)).expireBooking(BookingTestData.BOOKING_ID);
    }
}
