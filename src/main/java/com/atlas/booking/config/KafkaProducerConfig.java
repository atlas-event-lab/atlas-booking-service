package com.atlas.booking.config;

import com.atlas.booking.shared.messaging.EventTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka producer configuration.
 * KafkaTemplate and ProducerFactory are auto-configured from application.yml
 * (spring.kafka.producer.*). This class declares all booking.* topics owned by
 * Booking Service; KafkaAdmin creates them on startup if they do not exist (topics.md).
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public NewTopic bookingCreatedTopic() {
        return TopicBuilder.name(EventTopics.BOOKING_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic bookingConfirmedTopic() {
        return TopicBuilder.name(EventTopics.BOOKING_CONFIRMED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic bookingFailedTopic() {
        return TopicBuilder.name(EventTopics.BOOKING_FAILED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic bookingExpiredTopic() {
        return TopicBuilder.name(EventTopics.BOOKING_EXPIRED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
