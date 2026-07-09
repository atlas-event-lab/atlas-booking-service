package com.atlas.booking.booking.service;

import com.atlas.booking.booking.client.ExchangeRateClient;
import com.atlas.booking.booking.client.FlightPriceClient;
import com.atlas.booking.booking.client.HotelPriceClient;
import com.atlas.booking.booking.client.dto.FlightPriceResponse;
import com.atlas.booking.booking.client.dto.MoneyDto;
import com.atlas.booking.booking.entity.Booking;
import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.exception.BookingAccessDeniedException;
import com.atlas.booking.booking.exception.BookingNotFoundException;
import com.atlas.booking.booking.exception.BookingNotCancellableException;
import com.atlas.booking.booking.exception.CatalogUnavailableException;
import com.atlas.booking.booking.exception.CatalogValidationException;
import com.atlas.booking.booking.exception.IdempotencyConflictException;
import com.atlas.booking.booking.exception.PrematureSagaEventException;
import com.atlas.booking.booking.mapper.BookingMapper;
import com.atlas.booking.booking.messaging.OutboxEventWriter;
import com.atlas.booking.booking.repository.BookingRepository;
import com.atlas.booking.booking.repository.ConsumedEventRepository;
import com.atlas.booking.booking.support.BookingTestData;
import com.atlas.booking.shared.messaging.EventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

  @Mock
  BookingRepository bookingRepository;
  @Mock
  ConsumedEventRepository consumedEventRepository;
  @Mock
  FlightPriceClient flightPriceClient;
  @Mock
  HotelPriceClient hotelPriceClient;
  @Mock
  ObjectMapper objectMapper;
  @Mock
  OutboxEventWriter outboxEventWriter;
  @Mock
  BookingMapper bookingMapper;
  @Mock
  ExchangeRateClient exchangeRateClient;

  BookingServiceImpl bookingService;

  @BeforeEach
  void setUpJwt() {
    // Fixed clock so hotel stay-date validation ("checkIn >= today") is deterministic.
    Clock clock = Clock.fixed(LocalDate.of(2026, 6, 17).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    bookingService = new BookingServiceImpl(
        bookingRepository, consumedEventRepository, flightPriceClient, hotelPriceClient,
        objectMapper, outboxEventWriter, bookingMapper, exchangeRateClient,
        new HotelBookingProperties(30), clock);

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
  void createBooking_happyPath_persists_booking_and_publishes_event()
      throws JsonProcessingException {
    when(bookingRepository.findByIdempotencyKey(BookingTestData.IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(objectMapper.writeValueAsBytes(any())).thenReturn("payload".getBytes());
    when(flightPriceClient.getFlightPrice(BookingTestData.FLIGHT_ID))
        .thenReturn(BookingTestData.aFlightPriceResponse());
    when(hotelPriceClient.getRoomTypePrice(BookingTestData.HOTEL_ID, BookingTestData.ROOM_TYPE_ID))
        .thenReturn(BookingTestData.aRoomTypePriceResponse());
    when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    when(bookingMapper.toResponse(any(Booking.class))).thenReturn(
        BookingTestData.aBookingResponse());

    var result = bookingService.createBooking(
        BookingTestData.IDEMPOTENCY_KEY, BookingTestData.aCreateBookingRequest());

    assertThat(result.isReplay()).isFalse();
    assertThat(result.booking()).isEqualTo(BookingTestData.aBookingResponse());

    verify(bookingRepository).save(any(Booking.class));
    verify(outboxEventWriter).write(any(), eq(EventType.BOOKING_CREATED), any(), any(), any());
  }

  @Test
  void createBooking_idempotentReplay_returns_existing_booking() throws JsonProcessingException {
    byte[] payloadBytes = "payload".getBytes();
    when(objectMapper.writeValueAsBytes(any())).thenReturn(payloadBytes);

    // Compute the hash that the service will generate so the stored hash matches
    String storedHash = computeSha256Hex(payloadBytes);
    Booking existing = new Booking(
        BookingTestData.BOOKING_ID, BookingTestData.USER_ID,
        BookingStatus.PENDING,
        new com.atlas.booking.booking.entity.Money(BookingTestData.TOTAL_AMOUNT,
            BookingTestData.CURRENCY),
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
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void createBooking_idempotencyConflict_throws() throws JsonProcessingException {
    when(objectMapper.writeValueAsBytes(any())).thenReturn("payload".getBytes());

    Booking existing = new Booking(
        BookingTestData.BOOKING_ID, BookingTestData.USER_ID,
        BookingStatus.PENDING,
        new com.atlas.booking.booking.entity.Money(BookingTestData.TOTAL_AMOUNT,
            BookingTestData.CURRENCY),
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
  void createBooking_flightWithdrawn_throws422() throws JsonProcessingException {
    when(bookingRepository.findByIdempotencyKey(BookingTestData.IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(objectMapper.writeValueAsBytes(any())).thenReturn("payload".getBytes());
    when(flightPriceClient.getFlightPrice(BookingTestData.FLIGHT_ID))
        .thenReturn(new FlightPriceResponse(
            BookingTestData.FLIGHT_ID,
            new MoneyDto(BookingTestData.FLIGHT_UNIT_PRICE, BookingTestData.CURRENCY),
            "WITHDRAWN"));

    assertThatThrownBy(() ->
        bookingService.createBooking(
            BookingTestData.IDEMPOTENCY_KEY, BookingTestData.aCreateBookingRequest()))
        .isInstanceOf(CatalogValidationException.class)
        .hasMessageContaining("not ACTIVE");

    verify(bookingRepository, never()).save(any());
  }

  @Test
  void createBooking_priceMismatch_throws422() throws JsonProcessingException {
    when(bookingRepository.findByIdempotencyKey(BookingTestData.IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(objectMapper.writeValueAsBytes(any())).thenReturn("payload".getBytes());
    when(flightPriceClient.getFlightPrice(BookingTestData.FLIGHT_ID))
        .thenReturn(new FlightPriceResponse(
            BookingTestData.FLIGHT_ID,
            new MoneyDto(new java.math.BigDecimal("999.00"), BookingTestData.CURRENCY),
            "ACTIVE"));

    assertThatThrownBy(() ->
        bookingService.createBooking(
            BookingTestData.IDEMPOTENCY_KEY, BookingTestData.aCreateBookingRequest()))
        .isInstanceOf(CatalogValidationException.class)
        .hasMessageContaining("Price mismatch");

    verify(bookingRepository, never()).save(any());
  }

  @Test
  void createBooking_catalogUnavailable_throws503() throws JsonProcessingException {
    when(bookingRepository.findByIdempotencyKey(BookingTestData.IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(objectMapper.writeValueAsBytes(any())).thenReturn("payload".getBytes());

    Request feignRequest = Request.create(
        Request.HttpMethod.GET, "/api/v1/flights/" + BookingTestData.FLIGHT_ID + "/price",
        Collections.emptyMap(), null, new RequestTemplate());
    when(flightPriceClient.getFlightPrice(BookingTestData.FLIGHT_ID))
        .thenThrow(new FeignException.ServiceUnavailable(
            "Service Unavailable", feignRequest, null, null));

    assertThatThrownBy(() ->
        bookingService.createBooking(
            BookingTestData.IDEMPOTENCY_KEY, BookingTestData.aCreateBookingRequest()))
        .isInstanceOf(CatalogUnavailableException.class)
        .hasMessageContaining("Flight service unavailable");

    verify(bookingRepository, never()).save(any());
  }

  @Test
  void createBooking_hotelStayExceedsMaxStay_throws400() throws JsonProcessingException {
    when(bookingRepository.findByIdempotencyKey(BookingTestData.IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(objectMapper.writeValueAsBytes(any())).thenReturn("payload".getBytes());
    when(hotelPriceClient.getRoomTypePrice(BookingTestData.HOTEL_ID, BookingTestData.ROOM_TYPE_ID))
        .thenReturn(BookingTestData.aRoomTypePriceResponse());

    var longStayHotel = new com.atlas.booking.booking.dto.BookingItemSelectionRequest(
        com.atlas.booking.booking.entity.BookingItemType.HOTEL,
        BookingTestData.ROOM_TYPE_ID, BookingTestData.HOTEL_ID, 1,
        new MoneyDto(BookingTestData.HOTEL_UNIT_PRICE, BookingTestData.CURRENCY),
        LocalDate.of(2026, 6, 20), LocalDate.of(2026, 8, 1)); // 42 nights > 30
    var request = new com.atlas.booking.booking.dto.CreateBookingRequest(
        java.util.List.of(BookingTestData.aTraveler()),
        java.util.List.of(longStayHotel),
        new MoneyDto(new java.math.BigDecimal("6300.00"), BookingTestData.CURRENCY));

    assertThatThrownBy(() ->
        bookingService.createBooking(BookingTestData.IDEMPOTENCY_KEY, request))
        .isInstanceOf(com.atlas.booking.booking.exception.BookingValidationException.class)
        .hasMessageContaining("maximum");

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
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void onInventoryReserved_duplicate_event_is_skipped() {
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(true);

    bookingService.onInventoryReserved(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

    verify(bookingRepository, never()).findById(any());
    verify(consumedEventRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
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
    verify(outboxEventWriter).write(any(), eq(EventType.BOOKING_FAILED), any(), any(), any());
  }

  @Test
  void onInventoryRejected_duplicate_event_is_skipped() {
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(true);

    bookingService.onInventoryRejected(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

    verify(bookingRepository, never()).findById(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
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
    verify(outboxEventWriter).write(any(), eq(EventType.BOOKING_CONFIRMED), any(), any(), any());
  }

  @Test
  void onPaymentSucceeded_duplicate_event_is_skipped() {
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(true);

    bookingService.onPaymentSucceeded(
        BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID, BookingTestData.PAYMENT_ID);

    verify(bookingRepository, never()).findById(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  // ── Saga: onPaymentFailed ────────────────────────────────────────────────

  @Test
  void onPaymentFailed_transitions_INVENTORY_RESERVED_to_FAILED() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.INVENTORY_RESERVED);
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    bookingService.onPaymentFailed(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
    verify(outboxEventWriter).write(any(), eq(EventType.BOOKING_FAILED), any(), any(), any());
  }

  // ── Saga: onPaymentTimedOut ──────────────────────────────────────────────

  @Test
  void onPaymentTimedOut_transitions_INVENTORY_RESERVED_to_EXPIRED() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.INVENTORY_RESERVED);
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    bookingService.onPaymentTimedOut(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    verify(outboxEventWriter).write(any(), eq(EventType.BOOKING_EXPIRED), any(), any(), any());
  }

  // ── Saga: out-of-order payment arrivals (ADR-0007) ───────────────────────

  @Test
  void onPaymentSucceeded_in_PENDING_is_premature_and_retried() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.PENDING);
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    // payment.succeeded outran inventory.reserved → defer (retryable), do not confirm.
    assertThatThrownBy(() -> bookingService.onPaymentSucceeded(
            BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID, BookingTestData.PAYMENT_ID))
        .isInstanceOf(PrematureSagaEventException.class);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    // Idempotency row is NOT written, so the retry re-processes cleanly (ADR-0007 §Decision 4).
    verify(consumedEventRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void onPaymentFailed_in_PENDING_is_premature_and_retried() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.PENDING);
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    assertThatThrownBy(() -> bookingService.onPaymentFailed(
            BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID))
        .isInstanceOf(PrematureSagaEventException.class);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    verify(consumedEventRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void onPaymentTimedOut_in_PENDING_is_premature_and_retried() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.PENDING);
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    assertThatThrownBy(() -> bookingService.onPaymentTimedOut(
            BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID))
        .isInstanceOf(PrematureSagaEventException.class);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    verify(consumedEventRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void onPaymentSucceeded_in_terminal_state_still_DLQs() {
    // A payment event landing on a terminal Booking is a genuine anomaly → non-retryable.
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.FAILED);
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    assertThatThrownBy(() -> bookingService.onPaymentSucceeded(
            BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID, BookingTestData.PAYMENT_ID))
        .isInstanceOf(com.atlas.booking.booking.exception.InvalidStateTransitionException.class);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
    verify(consumedEventRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  // ── cancelBooking ────────────────────────────────────────────────────────

  @Test
  void cancelBooking_CONFIRMED_transitions_to_CANCELLING_and_publishes_BookingCancelled() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.CONFIRMED);
    when(bookingRepository.findByCancellationIdempotencyKey(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));
    when(bookingMapper.toResponse(booking)).thenReturn(
        BookingTestData.aBookingResponseWithStatus(
            com.atlas.booking.booking.dto.ApiBookingStatus.CONFIRMED));

    bookingService.cancelBooking(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY,
        BookingTestData.BOOKING_ID,
        BookingTestData.aCancelBookingRequest());

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLING);
    assertThat(booking.getCancelledAt()).isNull();
    assertThat(booking.getCancellationIdempotencyKey()).isEqualTo(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY);
    verify(outboxEventWriter).write(any(), eq(EventType.BOOKING_CANCELLED), any(), any(), any());
  }

  @Test
  void cancelBooking_PENDING_state_throws_409() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.PENDING);
    when(bookingRepository.findByCancellationIdempotencyKey(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    assertThatThrownBy(() ->
        bookingService.cancelBooking(
            BookingTestData.CANCELLATION_IDEMPOTENCY_KEY,
            BookingTestData.BOOKING_ID,
            BookingTestData.aCancelBookingRequest()))
        .isInstanceOf(BookingNotCancellableException.class);

    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void cancelBooking_INVENTORY_RESERVED_state_throws_409() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.INVENTORY_RESERVED);
    when(bookingRepository.findByCancellationIdempotencyKey(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    assertThatThrownBy(() ->
        bookingService.cancelBooking(
            BookingTestData.CANCELLATION_IDEMPOTENCY_KEY,
            BookingTestData.BOOKING_ID,
            BookingTestData.aCancelBookingRequest()))
        .isInstanceOf(BookingNotCancellableException.class);

    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void cancelBooking_reason_is_stored_in_status_history() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.CONFIRMED);
    when(bookingRepository.findByCancellationIdempotencyKey(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));
    when(bookingMapper.toResponse(any())).thenReturn(BookingTestData.aBookingResponse());

    bookingService.cancelBooking(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY,
        BookingTestData.BOOKING_ID,
        BookingTestData.aCancelBookingRequest());

    assertThat(booking.getStatusHistory())
        .filteredOn(h -> h.getNewStatus() == BookingStatus.CANCELLING)
        .hasSize(1)
        .first()
        .satisfies(h -> assertThat(h.getReason()).isEqualTo(BookingTestData.CANCELLATION_REASON));
  }

  @Test
  void cancelBooking_idempotentReplay_same_booking_returns_existing_without_republishing() {
    Booking existing = BookingTestData.aBookingWithStatus(BookingStatus.CANCELLING);
    when(bookingRepository.findByCancellationIdempotencyKey(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
        .thenReturn(Optional.of(existing));
    when(bookingMapper.toResponse(existing)).thenReturn(
        BookingTestData.aBookingResponseWithStatus(
            com.atlas.booking.booking.dto.ApiBookingStatus.CONFIRMED));

    bookingService.cancelBooking(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY,
        BookingTestData.BOOKING_ID,
        BookingTestData.aCancelBookingRequest());

    verify(bookingRepository, never()).findById(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void cancelBooking_idempotencyConflict_different_booking_throws() {
    Booking other = BookingTestData.aBookingWithStatus(BookingStatus.CANCELLED);
    UUID differentBookingId = UUID.fromString("99999999-9999-9999-9999-999999999999");
    when(bookingRepository.findByCancellationIdempotencyKey(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
        .thenReturn(Optional.of(other));

    assertThatThrownBy(() ->
        bookingService.cancelBooking(
            BookingTestData.CANCELLATION_IDEMPOTENCY_KEY,
            differentBookingId,
            BookingTestData.aCancelBookingRequest()))
        .isInstanceOf(IdempotencyConflictException.class);

    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void cancelBooking_bookingNotFound_throws() {
    when(bookingRepository.findByCancellationIdempotencyKey(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() ->
        bookingService.cancelBooking(
            BookingTestData.CANCELLATION_IDEMPOTENCY_KEY,
            BookingTestData.BOOKING_ID,
            BookingTestData.aCancelBookingRequest()))
        .isInstanceOf(BookingNotFoundException.class);
  }

  @Test
  void cancelBooking_notOwner_throws_403() {
    Booking booking = BookingTestData.aBookingOwnedByOtherUser(BookingStatus.CONFIRMED);
    when(bookingRepository.findByCancellationIdempotencyKey(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    assertThatThrownBy(() ->
        bookingService.cancelBooking(
            BookingTestData.CANCELLATION_IDEMPOTENCY_KEY,
            BookingTestData.BOOKING_ID,
            BookingTestData.aCancelBookingRequest()))
        .isInstanceOf(BookingAccessDeniedException.class);

    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void cancelBooking_CANCELLING_state_throws_409() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.CANCELLING);
    when(bookingRepository.findByCancellationIdempotencyKey(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    assertThatThrownBy(() ->
        bookingService.cancelBooking(
            BookingTestData.CANCELLATION_IDEMPOTENCY_KEY,
            BookingTestData.BOOKING_ID,
            BookingTestData.aCancelBookingRequest()))
        .isInstanceOf(BookingNotCancellableException.class);

    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void cancelBooking_CANCELLED_state_throws_409() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.CANCELLED);
    when(bookingRepository.findByCancellationIdempotencyKey(
        BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty());
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    assertThatThrownBy(() ->
        bookingService.cancelBooking(
            BookingTestData.CANCELLATION_IDEMPOTENCY_KEY,
            BookingTestData.BOOKING_ID,
            BookingTestData.aCancelBookingRequest()))
        .isInstanceOf(BookingNotCancellableException.class);
  }

  // ── onInventoryReleased ──────────────────────────────────────────────────

  @Test
  void onInventoryReleased_CANCELLING_transitions_to_CANCELLED() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.CANCELLING);
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    bookingService.onInventoryReleased(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    assertThat(booking.getCancelledAt()).isNotNull();
    verify(consumedEventRepository).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void onInventoryReleased_already_CANCELLED_is_noop() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.CANCELLED);
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(false);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    bookingService.onInventoryReleased(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    verify(consumedEventRepository).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void onInventoryReleased_duplicate_event_is_skipped() {
    when(consumedEventRepository.existsById(BookingTestData.EVENT_ID)).thenReturn(true);

    bookingService.onInventoryReleased(BookingTestData.EVENT_ID, BookingTestData.BOOKING_ID);

    verify(bookingRepository, never()).findById(any());
    verify(consumedEventRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  // ── expireBooking ────────────────────────────────────────────────────────

  @Test
  void expireBooking_PENDING_transitions_to_EXPIRED_and_publishes_BookingExpired() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.PENDING);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    bookingService.expireBooking(BookingTestData.BOOKING_ID);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    verify(outboxEventWriter).write(any(), eq(EventType.BOOKING_EXPIRED), any(), any(), any());
  }

  @Test
  void expireBooking_INVENTORY_RESERVED_is_noop() {
    // Addendum: INVENTORY_RESERVED → EXPIRED is owned by Payment's PaymentTimedOut,
    // never by the scheduler. The job must not transition it (no orphan-charge race).
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.INVENTORY_RESERVED);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    bookingService.expireBooking(BookingTestData.BOOKING_ID);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.INVENTORY_RESERVED);
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void expireBooking_terminal_state_is_noop() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.CONFIRMED);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    bookingService.expireBooking(BookingTestData.BOOKING_ID);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void expireBooking_already_EXPIRED_is_noop() {
    Booking booking = BookingTestData.aBookingWithStatus(BookingStatus.EXPIRED);
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.of(booking));

    bookingService.expireBooking(BookingTestData.BOOKING_ID);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
  }

  @Test
  void expireBooking_notFound_throws() {
    when(bookingRepository.findById(BookingTestData.BOOKING_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> bookingService.expireBooking(BookingTestData.BOOKING_ID))
        .isInstanceOf(BookingNotFoundException.class);
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
