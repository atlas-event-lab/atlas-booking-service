package com.atlas.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Search Service REST client (ARCH-006).
 * Bound from {@code clients.search.*} in application.yml (SEC-005: no hardcoded URLs).
 */
@ConfigurationProperties(prefix = "clients.search")
public record SearchClientProperties(String baseUrl) {}
