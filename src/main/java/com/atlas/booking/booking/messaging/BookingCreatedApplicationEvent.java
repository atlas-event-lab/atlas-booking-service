package com.atlas.booking.booking.messaging;

import com.atlas.booking.booking.event.BookingCreatedPayload;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Internal Spring ApplicationEvent published inside the DB transaction so that
 * {@link BookingEventPublisher} can send to Kafka via
 * {@code @TransactionalEventListener(AFTER_COMMIT)}, guaranteeing the DB row is
 * visible before the Kafka message is sent (OBS-002, OBS-003).
 */
@Getter
public class BookingCreatedApplicationEvent extends ApplicationEvent {

    private final UUID bookingId;
    private final String correlationId;
    private final UUID sagaId;
    private final BookingCreatedPayload payload;

    public BookingCreatedApplicationEvent(Object source, UUID bookingId,
                                          String correlationId, UUID sagaId,
                                          BookingCreatedPayload payload) {
        super(source);
        this.bookingId = bookingId;
        this.correlationId = correlationId;
        this.sagaId = sagaId;
        this.payload = payload;
    }

}
