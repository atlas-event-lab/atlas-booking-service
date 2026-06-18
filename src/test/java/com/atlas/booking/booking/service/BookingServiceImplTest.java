package com.atlas.booking.booking.service;

import com.atlas.booking.booking.client.SearchClient;
import com.atlas.booking.booking.entity.Booking;
import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.exception.BookingNotFoundException;
import com.atlas.booking.booking.exception.IdempotencyConflictException;
import com.atlas.booking.booking.exception.PricingMismatchException;
import com.atlas.booking.booking.exception.TripNotFoundException;
import com.atlas.booking.booking.mapper.BookingMapper;
import com.atlas.booking.booking.messaging.BookingCreatedApplicationEvent;
import com.atlas.booking.booking.messaging.BookingLifecycleApplicationEvent;
import com.atlas.booking.booking.repository.BookingRepository;
import com.atlas.booking.booking.repository.ConsumedEventRepository;
import com.atlas.booking.booking.support.BookingTestData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock BookingRepository         bookingRepository;
    @Mock ConsumedEventRepository   consumedEventRepository;
    @Mock SearchClient              searchClient;
    @Mock ObjectMapper              objectMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock BookingMapper             bookingMapper;

    @InjectMocks
    BookingServiceImpl bookingService;

    @BeforeEach
    void setUpJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", BookingTestData.USER_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── createBooking ────────────────────────────────────────────────────────

    @Test
    void createBooking_happyPath_persists_booking_and_publishes_event() throws JsonProcessingException {
        when(bookingRepository.findByIdempotencyKey(BookingTestData.IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(objectMapper.writeValueAsBytes(any())).thenReturn("payload".getBytes());
        when(searchClient.getTrip(BookingTestData.TRIP_ID))
                .thenReturn(Optional.of(BookingTestData.aTripDetail()));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingMapper.toResponse(any(Booking.class))).thenReturn(BookingTestData.aBookingResponse());

        var result = bookingService.createBooking(
                BookingTestData.IDEMPOTENCY_KEY, BookingTestData.aCreateBookingRequest());

        assertThat(result.isReplay()).isFalse();
        assertThat(result.booking()).isEqualTo(BookingTestData.aBookingResponse());

        verify(bookingRepository).save(any(Booking.class));
        verify(eventPublisher).publishEvent(any(BookingCreatedApplicationEvent.class));
    }

    @Test
    void createBooking_idempotentReplay_returns_existing_booking() throws JsonProcessingException {
        byte[] payloadBytes = "payload".getBytes();
        when(objectMapper.writeValueAsBytes(any())).thenReturn(payloadBytes);

        // Compute the hash that the service will generate so the stored hash matches
        String storedHash = computeSha256Hex(payloadBytes);
        Booking existing = new Booking(
                BookingTestData.BOOKING_ID, BookingTestData.USER_ID, BookingTestData.TRIP_ID,
                BookingStatus.PENDING,
                new com.atlas.booking.booking.entity.Money(BookingTestData.TOTAL_AMOUNT, BookingTestData.CURRENCY),
                BookingTestData.CORRELATION_ID, BookingTestData.SAGA_ID,
                BookingTestData.IDEMPOTENCY_KEY, storedHash);

        when(bookingRepository.findByIdempotencyKey(BookingTestData.IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));
        when(bookingMapper.toResponse(existing)).thenReturn(BookingTestData.aBookingResponse());

        var result = bookingService.createBooking(
                BookingTestData.IDEMPOTENCY_KEY, BookingTestData.aCreateBookingRequest());

        assertThat(result.isReplay()).isTrue();
        assertThat(result.booking()).isEqualTo(BookingTestData.aBookingResponse());
        verify(bookingRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createBooking_idempotencyConflict_throws() throws JsonProcessingException {
        when(objectMapper.writeValueAsBytes(any())).thenReturn("payload".getBytes());

        Booking existing = new Booking(
                BookingTestData.BOOKING_ID, BookingTestData.USER_ID, BookingTestData.TRIP_ID,
                BookingStatus.PENDING,
                new com.atlas.booking.booking.entity.Money(BookingTestData.TOTAL_AMOUNT, BookingTestData.CURRENCY),
                BookingTestData.CORRELATION_ID, BookingTestData.SAGA_ID,
                BookingTestData.IDEMPOTENCY_KEY, "completely-different-hash");

        when(bookingRepository.findByIdempotencyKey(BookingTestData.IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                bookingService.createBooking(
                        BookingTestData.IDEMPOTENCY_KEY, BookingTestData.aCreateBookingRequest()))
                .isInstanceOf(IdempotencyConflictException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_tripNotFound_throws() throws JsonProcessingException {
        when(bookingRepository.findByIdempotencyKey(BookingTestData.IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(objectMapper.writeValueAsBytes(any())).thenReturn("payload".getBytes());
        when(searchClient.getTrip(BookingTestData.TRIP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                bookingService.createBooking(
                        BookingTestData.IDEMPOTENCY_KEY, BookingTestData.aCreateBookingRequest()))
                .isInstanceOf(TripNotFoundException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_pricingMismatch_throws() throws JsonProcessingException {
        when(bookingRepository.findByIdempotencyKey(BookingTestData.IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(objectMapper.writeValueAsBytes(any())).thenReturn("payload".getBytes());
        when(searchClient.getTrip(BookingTestData.TRIP_ID))
                .thenReturn(Optional.of(BookingTestData.aTripDetailWithMismatchedTotal()));

        assertThatThrownBy(() ->
                bookingService.createBooking(
                        BookingTestData.IDEMPOTENCY_KEY, BookingTestData.aCreateBookingRequest()))
                .isInstanceOf(PricingMismatchException.class);

        verify(bookingRepository, never()).save(any());
    }

    // ── getBooking ───────────────────────────────────────────────────────────

    @Test
    void getBooking_exists_returns_dto() {
        Booking booking = BookingTestData.aBooking();
        when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingMapper.toResponse(booking)).thenReturn(BookingTestData.aBookingResponse());

        var response = bookingService.getBooking(BookingTestData.BOOKING_ID);

        assertThat(response).isEqualTo(BookingTestData.aBookingResponse());
    }

    @Test
    void getBooking_notFound_throws() {
        when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBooking(BookingTestData.BOOKING_ID))
                .isInstanceOf(BookingNotFoundException.class);
    }

    // ── Saga: onInventoryReserved ────────────────────────────────────────────

    @Test
    void onInventoryReserved_transitions_PENDING_to_INVENTORY_RESERVED() {
        Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.PENDING);
        when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
        when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

        bookingService.onInventoryReserved(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.INVENTORY_RESERVED);
        verify(consumedEventRepository).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void onInventoryReserved_duplicate_event_is_skipped() {
        when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(true);

        bookingService.onInventoryReserved(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

        verify(bookingRepository, never()).findById(any());
        verify(consumedEventRepository, never()).save(any());
    }

    // ── Saga: onInventoryRejected ────────────────────────────────────────────

    @Test
    void onInventoryRejected_transitions_PENDING_to_FAILED() {
        Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.PENDING);
        when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
        when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

        bookingService.onInventoryRejected(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
        verify(consumedEventRepository).save(any());
        verify(eventPublisher).publishEvent(any(BookingLifecycleApplicationEvent.class));
    }

    @Test
    void onInventoryRejected_duplicate_event_is_skipped() {
        when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(true);

        bookingService.onInventoryRejected(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

        verify(bookingRepository, never()).findById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── Saga: onPaymentSucceeded ─────────────────────────────────────────────

    @Test
    void onPaymentSucceeded_transitions_INVENTORY_RESERVED_to_CONFIRMED() {
        Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.INVENTORY_RESERVED);
        when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
        when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

        bookingService.onPaymentSucceeded(
                BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID, BookingTestData.PAYMENT_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPaymentId()).isEqualTo(BookingTestData.PAYMENT_ID);
        assertThat(booking.getConfirmedAt()).isNotNull();
        verify(consumedEventRepository).save(any());
        verify(eventPublisher).publishEvent(any(BookingLifecycleApplicationEvent.class));
    }

    @Test
    void onPaymentSucceeded_duplicate_event_is_skipped() {
        when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(true);

        bookingService.onPaymentSucceeded(
                BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID, BookingTestData.PAYMENT_ID);

        verify(bookingRepository, never()).findById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── Saga: onPaymentFailed ────────────────────────────────────────────────

    @Test
    void onPaymentFailed_transitions_INVENTORY_RESERVED_to_FAILED() {
        Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.INVENTORY_RESERVED);
        when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
        when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

        bookingService.onPaymentFailed(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
        verify(eventPublisher).publishEvent(any(BookingLifecycleApplicationEvent.class));
    }

    // ── Saga: onPaymentTimedOut ──────────────────────────────────────────────

    @Test
    void onPaymentTimedOut_transitions_INVENTORY_RESERVED_to_EXPIRED() {
        Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.INVENTORY_RESERVED);
        when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
        when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

        bookingService.onPaymentTimedOut(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(eventPublisher).publishEvent(any(BookingLifecycleApplicationEvent.class));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private static String computeSha256Hex(byte[] bytes) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
