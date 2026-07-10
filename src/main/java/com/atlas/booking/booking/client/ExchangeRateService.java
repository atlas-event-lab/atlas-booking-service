package com.atlas.booking.booking.client;

import com.atlas.booking.booking.client.dto.ExchangeRateDto;
import com.atlas.booking.booking.config.CacheConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Caches the USD exchange-rate table so pricing does not call the Frankfurter API on every
 * booking. The table is currency-independent, so it is cached under a single key with a
 * 15-minute TTL (see {@link CacheConfig}).
 *
 * <p>This is a dedicated bean (not a method on {@code BookingServiceImpl}) on purpose: Spring's
 * {@code @Cacheable} works only through the proxy, so a self-invocation inside
 * {@code BookingServiceImpl} would bypass the cache. Feign failures propagate (they are not
 * cached), so the caller's error handling still applies.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateClient exchangeRateClient;

    @Cacheable(cacheNames = CacheConfig.USD_EXCHANGE_RATES, key = "'ALL'")
    public List<ExchangeRateDto> getUSDExchangeRates() {
        return exchangeRateClient.getUSDExchangeRates();
    }
}
