package com.atlas.booking.booking.messaging;

import com.atlas.booking.booking.event.EventValidator;
import com.atlas.booking.booking.event.PaymentSucceededPayload;
import com.atlas.booking.booking.service.BookingService;
import com.atlas.booking.booking.support.BookingTestData;
import com.atlas.booking.booking.event.EventEnvelope;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingEventConsumerTest {

    @Mock BookingService bookingService;
    @Mock EventValidator eventValidator;

    @InjectMocks
    BookingEventConsumer consumer;

    @Test
    void onPaymentSucceeded_extracts_paymentId_from_its_own_field() {
        EventEnvelope<PaymentSucceededPayload> envelope = new EventEnvelope<>(
                BookingTestData.EVENT_ID,
                null, null, null, null, null, null, null,
                new PaymentSucceededPayload(BookingTestData.BOOKING_ID, BookingTestData.PAYMENT_ID));

        consumer.onPaymentSucceeded(envelope);

        // paymentId must come from payload.paymentId, not be a duplicate of bookingId.
        verify(bookingService).onPaymentSucceeded(
                BookingTestData.EVENT_ID,
                BookingTestData.BOOKING_ID,
                BookingTestData.PAYMENT_ID);
    }

    @Test
    void onPaymentSucceeded_missing_paymentId_is_rejected() {
        // Validation now lives in EventValidator: a null @NotNull paymentId on the
        // payload is rejected via bean validation (ConstraintViolationException), and
        // the booking transition is never invoked.
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            BookingEventConsumer realConsumer =
                    new BookingEventConsumer(bookingService, new EventValidator(validator));

            EventEnvelope<PaymentSucceededPayload> envelope = new EventEnvelope<>(
                    BookingTestData.EVENT_ID,
                    "PAYMENT_SUCCEEDED", null, null, null, null, null, null,
                    new PaymentSucceededPayload(BookingTestData.BOOKING_ID, null));

            assertThatThrownBy(() -> realConsumer.onPaymentSucceeded(envelope))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("paymentId");

            verify(bookingService, never()).onPaymentSucceeded(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
        }
    }
}
