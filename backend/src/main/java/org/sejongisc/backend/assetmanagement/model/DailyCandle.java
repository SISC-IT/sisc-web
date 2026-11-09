package org.sejongisc.backend.assetmanagement.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

/**
 * 일봉 한 건. 국내/미국 공통
 * - 가격은 모두 양수. 0 이하면 거절
 * - 종가는 필수, 시가·고가·저가는 키움이 생략해 보내면 null
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
