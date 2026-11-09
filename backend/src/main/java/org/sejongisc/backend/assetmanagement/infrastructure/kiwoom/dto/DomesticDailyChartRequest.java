package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DomesticDailyChartRequest {
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
