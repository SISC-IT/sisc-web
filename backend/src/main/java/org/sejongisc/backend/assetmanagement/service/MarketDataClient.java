package org.sejongisc.backend.assetmanagement.service;

import java.time.LocalDate;
import java.util.List;
import org.sejongisc.backend.assetmanagement.model.DailyCandle;
import org.sejongisc.backend.assetmanagement.model.UsExchangeType;

/**
 * 종목별 일봉 시세 조회. 계좌 조회({@link AssetManagementAccountClient})와 달리
 * 보유 여부와 무관하게 임의 종목의 확정 종가를 가져온다.
 */
public interface MarketDataClient {
  /**
   * 국내주식 일봉을 조회한다. 키움은 기준일자부터 과거 방향으로 데이터를 돌려준다.
   *
   * @param stockCode 종목코드 (거래소 접미사 포함 가능: 039490_NX)
   * @param baseDate  기준일자
   * @return 일자 오름차순 일봉 목록
   */
  List<DailyCandle> fetchDomesticDailyCandles(String stockCode, LocalDate baseDate);

  /**
   * 미국주식 일봉을 조회한다. 종가는 환율 미적용 USD 원본이다.
   *
   * @param exchangeType 거래소 (AMEX / NASDAQ / NYSE)
   * @param stockCode    티커
   * @param startDate    시작일자
   * @return 일자 오름차순 일봉 목록
   */
  List<DailyCandle> fetchUsDailyCandles(
      UsExchangeType exchangeType, String stockCode, LocalDate startDate);
}
