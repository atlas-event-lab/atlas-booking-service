package com.atlas.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA Auditing so that {@code @CreatedDate} and
 * {@code @LastModifiedDate} are populated automatically on persist and update
 * (coding-standards §Spring Boot — avoid @PrePersist/@PreUpdate for timestamps).
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {}
