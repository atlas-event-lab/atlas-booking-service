package com.atlas.booking.booking.client.dto;

import java.math.BigDecimal;

public record ExchangeRateDto(
    String base,
    String quote,
    BigDecimal rate
) { }
