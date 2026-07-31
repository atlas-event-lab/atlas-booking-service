package com.atlas.booking.booking.config;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.tracing.MicrometerTracing;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Lettuce Redis client to Micrometer Observation so cache GET/SET calls produce spans in
 * the trace. Spring Boot's {@code LettuceConnectionFactory} picks up this {@link ClientResources}
 * bean automatically. Only the USD exchange-rate cache runs through Redis here, so the overhead is
 * negligible; it just completes the trace on the booking hot path.
 */
@Configuration
public class RedisTracingConfig {

    @Bean
    ClientResources lettuceClientResources(ObservationRegistry observationRegistry) {
        return ClientResources.builder()
                .tracing(new MicrometerTracing(observationRegistry, "redis"))
                .build();
    }
}
