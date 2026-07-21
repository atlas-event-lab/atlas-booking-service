package com.atlas.booking.booking.config;

import com.atlas.booking.booking.client.dto.ExchangeRateDto;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Redis-backed caching for booking-service. Today it caches only the USD exchange-rate table
 * ({@link com.atlas.booking.booking.client.ExchangeRateService}) so pricing does not hit the
 * Frankfurter API on every booking. TTL defaults to 15 minutes
 * ({@code booking.exchange-rate.cache-ttl} / {@code BOOKING_EXCHANGE_RATE_CACHE_TTL}).
 *
 * <p>Values are stored as <b>plain JSON without type metadata</b> (no {@code @class}) so the entry
 * is <b>shared</b> with travel-cart-service: both write/read the same key
 * ({@code usdExchangeRates::ALL}) with the same JSON shape, so both price with the identical rate
 * table and never disagree at checkout. Keep the cache name and key in sync across the two services.
 *
 * <p>Cache failures degrade gracefully: if Redis is unavailable, get/put errors are logged and
 * ignored so the underlying method still runs — a Redis blip never fails a booking (hot-path saga).
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    /** Cache holding the USD exchange-rate table under a single key. Shared with travel-cart. */
    public static final String USD_EXCHANGE_RATES = "usdExchangeRates";

    /**
     * Default Redis cache config: configurable TTL (15m default) and a type-agnostic JSON serializer
     * bound to {@code List<ExchangeRateDto>} (the only cached value), so cart and booking can read
     * each other's entry despite living in different packages.
     */
    @Bean
    RedisCacheConfiguration redisCacheConfiguration(@Value("${booking.exchange-rate.cache-ttl:15m}") Duration ttl) {
        ObjectMapper mapper = new ObjectMapper();
        JavaType rateListType = mapper.getTypeFactory().constructCollectionType(List.class, ExchangeRateDto.class);
        Jackson2JsonRedisSerializer<Object> valueSerializer = new Jackson2JsonRedisSerializer<>(mapper, rateListType);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
    }

    /** Never let a Redis outage fail a booking — log the cache error and fall back to the source. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(
                    @NonNull RuntimeException exception, @NonNull Cache cache, @NonNull Object key) {
                log.warn(
                        "Redis cache GET failed (cache={}, key={}) — falling back to source: {}",
                        cache.getName(),
                        key,
                        exception.getMessage());
            }

            @Override
            public void handleCachePutError(
                    @NonNull RuntimeException exception, @NonNull Cache cache, @NonNull Object key, Object value) {
                log.warn(
                        "Redis cache PUT failed (cache={}, key={}) — continuing without caching: {}",
                        cache.getName(),
                        key,
                        exception.getMessage());
            }
        };
    }
}
