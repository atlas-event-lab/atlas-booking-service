package com.atlas.booking.booking.controller;

import com.atlas.booking.booking.client.dto.MoneyDto;
import com.atlas.booking.booking.dto.ApiBookingStatus;
import com.atlas.booking.booking.dto.CancelBookingRequest;
import com.atlas.booking.booking.dto.CreateBookingRequest;
import com.atlas.booking.booking.exception.BookingAccessDeniedException;
import com.atlas.booking.booking.exception.BookingNotFoundException;
import com.atlas.booking.booking.exception.BookingNotCancellableException;
import com.atlas.booking.booking.exception.IdempotencyConflictException;
import com.atlas.booking.booking.exception.PricingMismatchException;
import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.service.BookingCreationResult;
import com.atlas.booking.booking.service.BookingService;
import com.atlas.booking.booking.support.BookingTestData;
import com.atlas.booking.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@Import(SecurityConfig.class)
class BookingControllerTest {

    @Autowired MockMvc     mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean BookingService bookingService;
    @MockitoBean  JwtDecoder     jwtDecoder;

    private static final String BASE_URL = "/api/v1/bookings";

    // ── POST /api/v1/bookings ────────────────────────────────────────────────

    @Test
    void createBooking_newBooking_returns_201() throws Exception {
        when(bookingService.createBooking(
                eq(BookingTestData.IDEMPOTENCY_KEY), any(CreateBookingRequest.class)))
                .thenReturn(new BookingCreationResult(BookingTestData.aBookingResponse(), false));

        mvc.perform(post(BASE_URL)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCreateBookingRequest())))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bookingId").value(BookingTestData.BOOKING_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createBooking_idempotentReplay_returns_200() throws Exception {
        when(bookingService.createBooking(
                eq(BookingTestData.IDEMPOTENCY_KEY), any(CreateBookingRequest.class)))
                .thenReturn(new BookingCreationResult(BookingTestData.aBookingResponse(), true));

        mvc.perform(post(BASE_URL)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCreateBookingRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(BookingTestData.BOOKING_ID.toString()));
    }

    @Test
    void createBooking_missingIdempotencyKeyHeader_returns_400() throws Exception {
        mvc.perform(post(BASE_URL)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCreateBookingRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void createBooking_invalidBody_returns_400() throws Exception {
        String invalidBody = """
                {"travelers": [], "items": []}
                """;

        mvc.perform(post(BASE_URL)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void createBooking_idempotencyConflict_returns_409() throws Exception {
        when(bookingService.createBooking(
                eq(BookingTestData.IDEMPOTENCY_KEY), any(CreateBookingRequest.class)))
                .thenThrow(new IdempotencyConflictException(BookingTestData.IDEMPOTENCY_KEY));

        mvc.perform(post(BASE_URL)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCreateBookingRequest())))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void createBooking_pricingMismatch_returns_422() throws Exception {
        when(bookingService.createBooking(
                eq(BookingTestData.IDEMPOTENCY_KEY), any(CreateBookingRequest.class)))
                .thenThrow(new PricingMismatchException("Total mismatch"));

        mvc.perform(post(BASE_URL)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCreateBookingRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void createBooking_unauthenticated_returns_401() throws Exception {
        mvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", BookingTestData.IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCreateBookingRequest())))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/bookings/{bookingId} ─────────────────────────────────────

    @Test
    void getBooking_found_returns_200() throws Exception {
        when(bookingService.getBooking(BookingTestData.BOOKING_ID))
                .thenReturn(BookingTestData.aBookingResponse());

        mvc.perform(get(BASE_URL + "/{bookingId}", BookingTestData.BOOKING_ID)
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bookingId").value(BookingTestData.BOOKING_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getBooking_notFound_returns_404() throws Exception {
        when(bookingService.getBooking(BookingTestData.BOOKING_ID))
                .thenThrow(new BookingNotFoundException(BookingTestData.BOOKING_ID));

        mvc.perform(get(BASE_URL + "/{bookingId}", BookingTestData.BOOKING_ID)
                        .with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void getBooking_unauthenticated_returns_401() throws Exception {
        mvc.perform(get(BASE_URL + "/{bookingId}", BookingTestData.BOOKING_ID))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/bookings/{bookingId}/cancellation ───────────────────────

    @Test
    void cancelBooking_happyPath_returns_200() throws Exception {
        when(bookingService.cancelBooking(
                eq(BookingTestData.CANCELLATION_IDEMPOTENCY_KEY),
                eq(BookingTestData.BOOKING_ID),
                any(CancelBookingRequest.class)))
                .thenReturn(BookingTestData.aBookingResponseWithStatus(ApiBookingStatus.CONFIRMED));

        mvc.perform(post(BASE_URL + "/{bookingId}/cancellation", BookingTestData.BOOKING_ID)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.CANCELLATION_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCancelBookingRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bookingId").value(BookingTestData.BOOKING_ID.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancelBooking_confirmedPath_returns_200_with_CONFIRMED_status() throws Exception {
        when(bookingService.cancelBooking(
                eq(BookingTestData.CANCELLATION_IDEMPOTENCY_KEY),
                eq(BookingTestData.BOOKING_ID),
                any(CancelBookingRequest.class)))
                .thenReturn(BookingTestData.aBookingResponseWithStatus(ApiBookingStatus.CONFIRMED));

        mvc.perform(post(BASE_URL + "/{bookingId}/cancellation", BookingTestData.BOOKING_ID)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.CANCELLATION_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCancelBookingRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancelBooking_withoutBody_returns_200() throws Exception {
        when(bookingService.cancelBooking(
                eq(BookingTestData.CANCELLATION_IDEMPOTENCY_KEY),
                eq(BookingTestData.BOOKING_ID),
                eq(null)))
                .thenReturn(BookingTestData.aBookingResponseWithStatus(ApiBookingStatus.CONFIRMED));

        mvc.perform(post(BASE_URL + "/{bookingId}/cancellation", BookingTestData.BOOKING_ID)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.CANCELLATION_IDEMPOTENCY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancelBooking_missingIdempotencyKeyHeader_returns_400() throws Exception {
        mvc.perform(post(BASE_URL + "/{bookingId}/cancellation", BookingTestData.BOOKING_ID)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCancelBookingRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cancelBooking_unauthenticated_returns_401() throws Exception {
        mvc.perform(post(BASE_URL + "/{bookingId}/cancellation", BookingTestData.BOOKING_ID)
                        .header("Idempotency-Key", BookingTestData.CANCELLATION_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCancelBookingRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelBooking_notFound_returns_404() throws Exception {
        when(bookingService.cancelBooking(any(), eq(BookingTestData.BOOKING_ID), any()))
                .thenThrow(new BookingNotFoundException(BookingTestData.BOOKING_ID));

        mvc.perform(post(BASE_URL + "/{bookingId}/cancellation", BookingTestData.BOOKING_ID)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.CANCELLATION_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCancelBookingRequest())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cancelBooking_notOwner_returns_403() throws Exception {
        when(bookingService.cancelBooking(any(), eq(BookingTestData.BOOKING_ID), any()))
                .thenThrow(new BookingAccessDeniedException(BookingTestData.BOOKING_ID));

        mvc.perform(post(BASE_URL + "/{bookingId}/cancellation", BookingTestData.BOOKING_ID)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.CANCELLATION_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCancelBookingRequest())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cancelBooking_nonCancellableState_returns_409() throws Exception {
        when(bookingService.cancelBooking(any(), eq(BookingTestData.BOOKING_ID), any()))
                .thenThrow(new BookingNotCancellableException(BookingTestData.BOOKING_ID, BookingStatus.CANCELLED));

        mvc.perform(post(BASE_URL + "/{bookingId}/cancellation", BookingTestData.BOOKING_ID)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.CANCELLATION_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCancelBookingRequest())))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cancelBooking_idempotencyConflict_returns_409() throws Exception {
        when(bookingService.cancelBooking(any(), eq(BookingTestData.BOOKING_ID), any()))
                .thenThrow(new IdempotencyConflictException(BookingTestData.CANCELLATION_IDEMPOTENCY_KEY));

        mvc.perform(post(BASE_URL + "/{bookingId}/cancellation", BookingTestData.BOOKING_ID)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.CANCELLATION_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingTestData.aCancelBookingRequest())))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cancelBooking_reasonExceedsMaxLength_returns_400() throws Exception {
        var request = new CancelBookingRequest("x".repeat(501));

        mvc.perform(post(BASE_URL + "/{bookingId}/cancellation", BookingTestData.BOOKING_ID)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.CANCELLATION_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ── Validation: travelers list ───────────────────────────────────────────

    @Test
    void createBooking_emptyTravelersList_returns_400() throws Exception {
        var request = new CreateBookingRequest(List.of(), List.of(), new MoneyDto(BigDecimal.ZERO, "USD"));

        mvc.perform(post(BASE_URL)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
