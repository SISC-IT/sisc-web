package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * usa06012 미국주식 일 차트 응답.
 *
 * <p>국내 일봉과 달리 응답에 종목코드가 없어, 호출 시 사용한 종목코드를 그대로 쓴다.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsDailyChartResponse implements KiwoomResponse {
  @JsonProperty("result_list")
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
    /** 현재가(종가). 단위 USD, 소수점 넷째 자리까지. */
    @JsonProperty("cur_prc")
    private String closePrice;

    @JsonProperty("pred_pre")
    private String previousDayDiff;

    @JsonProperty("flu_rt")
    private String fluctuationRate;

    @JsonProperty("acc_trde_qty")
    private String accumulatedVolume;

    @JsonProperty("acc_trde_prica")
    private String accumulatedTradeAmount;

    @JsonProperty("open_pric")
    private String openPrice;

    @JsonProperty("high_pric")
    private String highPrice;

    @JsonProperty("low_pric")
    private String lowPrice;

    @JsonProperty("dt")
    private String date;

    @JsonProperty("upd_stkpc_tp")
    private String adjustedPriceType;

    @JsonProperty("upd_rt")
    private String adjustedRate;
  }
}
