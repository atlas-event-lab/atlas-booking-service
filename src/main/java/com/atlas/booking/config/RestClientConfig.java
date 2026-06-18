package com.atlas.booking.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures the RestClient used to call the Search Service (ARCH-006).
 */
@Configuration
@EnableConfigurationProperties(SearchClientProperties.class)
public class RestClientConfig {

    @Bean
    public RestClient searchRestClient(SearchClientProperties properties) {
        System.out.println("Search Base URL = " + properties.baseUrl());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
