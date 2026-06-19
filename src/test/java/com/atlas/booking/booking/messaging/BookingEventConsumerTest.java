package com.atlas.booking.booking.messaging;

import com.atlas.booking.booking.service.BookingService;
import com.atlas.booking.booking.support.BookingTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingEventConsumerTest {

    @Mock BookingService bookingService;

    @InjectMocks
    BookingEventConsumer consumer;

    @Test
    void onPaymentSucceeded_extracts_paymentId_from_its_own_field() {
        Map<String, Object> envelope = Map.of(
                "eventId", BookingTestData.EVENT_ID.toString(),
                "payload", Map.of(
                        "bookingId", BookingTestData.BOOKING_ID.toString(),
                        "paymentId", BookingTestData.PAYMENT_ID.toString()));

        consumer.onPaymentSucceeded(envelope);

        // paymentId must come from payload.paymentId, not be a duplicate of bookingId.
        verify(bookingService).onPaymentSucceeded(
                BookingTestData.EVENT_ID,
                BookingTestData.BOOKING_ID,
                BookingTestData.PAYMENT_ID);
    }

    @Test
    void onPaymentSucceeded_missing_paymentId_is_rejected() {
        Map<String, Object> envelope = Map.of(
                "eventId", BookingTestData.EVENT_ID.toString(),
                "payload", Map.of("bookingId", BookingTestData.BOOKING_ID.toString()));

        assertThatThrownBy(() -> consumer.onPaymentSucceeded(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentId");
    }
}
