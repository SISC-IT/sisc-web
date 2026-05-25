package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyBalanceResponse implements KiwoomResponse {
  @JsonProperty("dt")
  private String date;

  @JsonProperty("tot_buy_amt")
  private String totalBuyAmount;

  @JsonProperty("tot_evlt_amt")
  private String totalEvaluationAmount;

  @JsonProperty("tot_evlt_prft")
  private String totalEvaluationProfit;

  @JsonProperty("tot_prft_rt")
  private String totalProfitRate;

  @JsonProperty("dbst_bal")
  private String depositBalance;

  @JsonProperty("day_stk_asst")
  private String dailyStockAsset;

  @JsonProperty("buy_wght")
  private String buyWeight;

  @JsonProperty("day_bal_rt")
  private List<StockDetail> dailyBalanceRate;

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
  public static class StockDetail {
    @JsonProperty("cur_prc")
    private String currentPrice;

    @JsonProperty("stk_cd")
    private String stockCode;

    @JsonProperty("stk_nm")
    private String stockName;

    @JsonProperty("rmnd_qty")
    private String remainderQuantity;

    @JsonProperty("buy_uv")
    private String buyUnitValue;

    @JsonProperty("buy_wght")
    private String buyWeight;

    @JsonProperty("evltv_prft")
    private String evaluationProfit;

    @JsonProperty("prft_rt")
    private String profitRate;

    @JsonProperty("evlt_amt")
    private String evaluationAmount;

    @JsonProperty("evlt_wght")
    private String evaluationWeight;
  }
}
