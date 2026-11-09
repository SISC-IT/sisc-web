package org.sejongisc.backend.assetmanagement.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

/**
 * 일봉 한 건. 국내/미국 공통으로 사용하는 도메인 모델이다.
 *
 * <p>키움 응답은 모든 수치가 문자열로 오므로 클라이언트에서 타입을 변환한 값을 담는다.
 * 가격 필드는 키움에서 양수로 오며, 0 이하 값이 오면 클라이언트가 거절하기 때문에
 * 이 모델에 담기는 가격은 항상 양수다.
 *
 * @param ticker      종목코드 (국내: 005930, 미국: AAPL)
 * @param date        일자 (KST 기준 거래일)
 * @param closePrice  종가 (필수. 누락되거나 0 이하면 클라이언트가 거절한다)
 * @param openPrice   시가 (키움이 생략해 보내면 {@code null})
 * @param highPrice   고가 (키움이 생략해 보내면 {@code null})
 * @param lowPrice    저가 (키움이 생략해 보내면 {@code null})
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
