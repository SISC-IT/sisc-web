package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ka10081 주식일봉차트조회요청 본문.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DomesticDailyChartRequest {
  /** 수정주가 적용. 액면분할/증자 등 권리 발생 이력을 반영한 가격을 받는다. */
  private static final String ADJUSTED_PRICE_APPLIED = "1";

  @JsonProperty("stk_cd")
  private String stockCode;

  @JsonProperty("base_dt")
  private String baseDate;

  @JsonProperty("upd_stkpc_tp")
  private String adjustedPriceType;

  public static DomesticDailyChartRequest of(String stockCode, String baseDate) {
    return new DomesticDailyChartRequest(stockCode, baseDate, ADJUSTED_PRICE_APPLIED);
  }
}
