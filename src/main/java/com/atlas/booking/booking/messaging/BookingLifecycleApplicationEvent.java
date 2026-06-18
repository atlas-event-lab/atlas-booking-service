package com.atlas.booking.booking.messaging;

import com.atlas.booking.booking.event.BookingLifecyclePayload;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Internal Spring ApplicationEvent published inside the DB transaction for
 * BookingConfirmed, BookingFailed and BookingExpired.
 * {@link BookingEventPublisher} handles it via
 * {@code @TransactionalEventListener(AFTER_COMMIT)} — the DB row is committed before
 * the Kafka message is sent (OBS-002, OBS-003).
 */
@Getter
public class BookingLifecycleApplicationEvent extends ApplicationEvent {

    private final String eventType;
    private final String kafkaTopic;
    private final UUID bookingId;
    private final String correlationId;
    private final String sagaId;
    private final BookingLifecyclePayload payload;

    public BookingLifecycleApplicationEvent(Object source,
                                            String eventType,
                                            String kafkaTopic,
                                            UUID bookingId,
                                            String correlationId,
                                            String sagaId,
                                            BookingLifecyclePayload payload) {
        super(source);
        this.eventType = eventType;
        this.kafkaTopic = kafkaTopic;
        this.bookingId = bookingId;
        this.correlationId = correlationId;
        this.sagaId = sagaId;
        this.payload = payload;
    }

}
