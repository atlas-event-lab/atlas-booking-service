package com.atlas.booking.booking.messaging;

import com.atlas.booking.booking.entity.OutboxEvent;
import com.atlas.booking.booking.entity.OutboxStatus;
import com.atlas.booking.booking.repository.OutboxRepository;
import com.atlas.booking.shared.messaging.EventTopics;
import com.atlas.booking.shared.messaging.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock OutboxRepository outboxRepository;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishesBookingCancelled_toCancelledTopic() {
        UUID bookingId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), "Booking", bookingId, EventType.BOOKING_CANCELLED, 1,
                "{\"eventType\":\"BookingCancelled\",\"payload\":{\"bookingId\":\"" + bookingId + "\"}}");

        when(outboxRepository.findTop100ByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        new OutboxRelay(outboxRepository, kafkaTemplate).publishPending();

        verify(kafkaTemplate).send(eq(EventTopics.BOOKING_CANCELLED), eq(bookingId.toString()), any());
    }
}
