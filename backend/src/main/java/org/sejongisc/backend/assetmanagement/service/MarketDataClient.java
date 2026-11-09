package org.sejongisc.backend.assetmanagement.service;

import java.time.LocalDate;
import java.util.List;
import org.sejongisc.backend.assetmanagement.model.DailyCandle;
import org.sejongisc.backend.assetmanagement.model.UsExchangeType;

public interface MarketDataClient {
  /** 일자 오름차순으로 반환한다. */
  List<DailyCandle> fetchDomesticDailyCandles(String stockCode, LocalDate baseDate);

  /** 일자 오름차순으로 반환한다. 종가는 환율 미적용 USD 원본이다. */
  List<DailyCandle> fetchUsDailyCandles(
      UsExchangeType exchangeType, String stockCode, LocalDate startDate);
}
