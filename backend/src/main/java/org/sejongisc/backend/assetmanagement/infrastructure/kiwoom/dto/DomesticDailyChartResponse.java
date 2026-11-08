package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ka10081 주식일봉차트조회요청 응답.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DomesticDailyChartResponse implements KiwoomResponse {
  @JsonProperty("stk_cd")
  private String stockCode;

  @JsonProperty("stk_dt_pole_chart_qry")
  private List<Candle> candles;

  @JsonProperty("return_code")
  private String returnCode;

  @JsonProperty("return_msg")
  private String returnMsg;

  @Override
  public String returnCode() {
    return returnCode;
  }

  @Override
  public String returnMsg() {
    return returnMsg;
  }

  @Getter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Candle {
    /** 종가. 하락일에는 {@code -60000} 처럼 부호가 붙어 오므로 절댓값 처리가 필요하다. */
    @JsonProperty("cur_prc")
    private String closePrice;

    @JsonProperty("trde_qty")
    private String tradeVolume;

    @JsonProperty("trde_prica")
    private String tradeAmount;

    @JsonProperty("dt")
    private String date;

    @JsonProperty("open_pric")
    private String openPrice;

    @JsonProperty("high_pric")
    private String highPrice;

    @JsonProperty("low_pric")
    private String lowPrice;

    @JsonProperty("pred_pre")
    private String previousDayDiff;

    @JsonProperty("pred_pre_sig")
    private String previousDayDiffSign;

    @JsonProperty("trde_tern_rt")
    private String tradeTurnoverRate;
  }
}
