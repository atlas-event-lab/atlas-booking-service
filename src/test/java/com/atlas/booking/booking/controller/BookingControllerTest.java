package com.atlas.booking.booking.controller;

import com.atlas.booking.booking.dto.CreateBookingRequest;
import com.atlas.booking.booking.exception.BookingNotFoundException;
import com.atlas.booking.booking.exception.IdempotencyConflictException;
import com.atlas.booking.booking.exception.PricingMismatchException;
import com.atlas.booking.booking.service.BookingCreationResult;
import com.atlas.booking.booking.service.BookingService;
import com.atlas.booking.booking.support.BookingTestData;
import com.atlas.booking.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
    @MockBean  BookingService bookingService;
    @MockBean  JwtDecoder     jwtDecoder;

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
                .andExpect(jsonPath("$.tripId").value(BookingTestData.TRIP_ID.toString()))
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

    // ── Validation: travelers list ───────────────────────────────────────────

    @Test
    void createBooking_emptyTravelersList_returns_400() throws Exception {
        var request = new CreateBookingRequest(
                BookingTestData.TRIP_ID,
                List.of(),
                List.of(BookingTestData.aFlightItem()));

        mvc.perform(post(BASE_URL)
                        .with(jwt())
                        .header("Idempotency-Key", BookingTestData.IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
