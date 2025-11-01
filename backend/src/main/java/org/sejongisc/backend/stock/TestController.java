package org.sejongisc.backend.stock;


import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.stock.entity.PriceData;
import org.sejongisc.backend.stock.repository.PriceDataRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TestController {

  private final PriceDataRepository priceDataRepository;

  @PostMapping("/test")
  public List<PriceData> test() {
    return priceDataRepository.findByTicker("AAPL");
  }

}
