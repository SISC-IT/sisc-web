package org.sejongisc.backend.assetmanagement.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

/**
 * 일봉 한 건. 국내/미국 공통으로 사용하는 도메인 모델이다.
 *
 * <p>키움 응답은 모든 수치가 문자열이고 국내 종가는 부호가 붙어 올 수 있어
 * ({@code -60000} 형태로 하락을 표시) 클라이언트에서 정규화한 값을 담는다.
 *
 * @param ticker      종목코드 (국내: 005930, 미국: AAPL)
 * @param date        일자 (KST 기준 거래일)
 * @param closePrice  종가 (항상 양수)
 * @param openPrice   시가
 * @param highPrice   고가
 * @param lowPrice    저가
 * @param volume      거래량
 * @param currency    통화 (KRW / USD)
 */
@Builder
public record DailyCandle(
    String ticker,
    LocalDate date,
    BigDecimal closePrice,
    BigDecimal openPrice,
    BigDecimal highPrice,
    BigDecimal lowPrice,
    Long volume,
    String currency
) {
}
