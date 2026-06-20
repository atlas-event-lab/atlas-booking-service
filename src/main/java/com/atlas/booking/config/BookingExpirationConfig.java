package com.atlas.booking.config;

import com.atlas.booking.booking.scheduler.BookingExpirationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the Booking expiration job: binds {@link BookingExpirationProperties} and exposes a
 * {@link Clock} bean so the scheduler's deadline check is deterministic and testable
 * (coding-standards §Unit Tests — "Tests SHALL be deterministic. No sleeps").
 */
@Configuration
@EnableConfigurationProperties(BookingExpirationProperties.class)
public class BookingExpirationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
