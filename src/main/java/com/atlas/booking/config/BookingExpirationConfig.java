package com.atlas.booking.config;

import com.atlas.booking.booking.scheduler.BookingExpirationProperties;
import com.atlas.booking.booking.service.HotelBookingProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Booking expiration job and hotel booking rules: binds
 * {@link BookingExpirationProperties} / {@link HotelBookingProperties} and exposes a {@link Clock}
 * bean so deadline / stay-date checks are deterministic and testable (coding-standards §Unit Tests —
 * "Tests SHALL be deterministic. No sleeps").
 */
@Configuration
@EnableConfigurationProperties({BookingExpirationProperties.class, HotelBookingProperties.class})
public class BookingExpirationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
