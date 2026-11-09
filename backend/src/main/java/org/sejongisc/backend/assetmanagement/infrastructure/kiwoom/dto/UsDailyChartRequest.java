package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsDailyChartRequest {
  private static final String ADJUSTED_PRICE_APPLIED = "1";

  /**
   * 환율 미적용. USD 원본으로 받아 환산은 서버에서 처리
   * - 키움 환율은 기준 시각을 통제할 수 없어 수익률 재계산 불가
   */
  private static final String EXCHANGE_RATE_NOT_APPLIED = "0";

  @JsonProperty("stex_tp")
  private String exchangeType;

  @JsonProperty("stk_cd")
  private String stockCode;

  @JsonProperty("strt_dt")
  private String startDate;

  @JsonProperty("upd_stkpc_tp")
  private String adjustedPriceType;

  @JsonProperty("exrt_appl_tp")
  private String exchangeRateAppliedType;

  public static UsDailyChartRequest of(String exchangeType, String stockCode, String startDate) {
    return new UsDailyChartRequest(
        exchangeType,
        stockCode,
        startDate,
        ADJUSTED_PRICE_APPLIED,
        EXCHANGE_RATE_NOT_APPLIED
    );
  }
}
