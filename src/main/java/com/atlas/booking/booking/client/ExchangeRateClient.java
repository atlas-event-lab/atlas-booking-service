package com.atlas.booking.booking.client;

import com.atlas.booking.booking.client.dto.ExchangeRateDto;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
    name = "frankfurter-api",
    url = "${clients.frankfurter.base-url}"
)
public interface ExchangeRateClient {

  @GetMapping("/v2/rates?base=USD")
  List<ExchangeRateDto> getUSDExchangeRates();
}
