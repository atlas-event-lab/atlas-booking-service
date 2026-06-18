package com.atlas.booking.booking.client.dto;

import java.math.BigDecimal;

/** Money representation from the Search API response (search.yaml Money schema). */
public record MoneyDto(BigDecimal amount, String currency) {}
