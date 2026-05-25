package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountEvaluationResponse implements KiwoomResponse {
  @JsonProperty("acnt_nm")
  private String accountName;

  @JsonProperty("brch_nm")
  private String branchName;

  @JsonProperty("entr")
  private String deposit;

  @JsonProperty("d2_entra")
  private String d2Deposit;

  @JsonProperty("tot_est_amt")
  private String totalEstimatedAmount;

  @JsonProperty("aset_evlt_amt")
  private String assetEvaluationAmount;

  @JsonProperty("tot_pur_amt")
  private String totalPurchaseAmount;

  @JsonProperty("prsm_dpst_aset_amt")
  private String presumedDepositAssetAmount;

  @JsonProperty("tot_grnt_sella")
  private String totalGuaranteeSellAmount;

  @JsonProperty("tdy_lspft_amt")
  private String todayInvestmentPrincipal;

  @JsonProperty("invt_bsamt")
  private String thisMonthInvestmentPrincipal;

  @JsonProperty("lspft_amt")
  private String accumulatedInvestmentPrincipal;

  @JsonProperty("tdy_lspft")
  private String todayProfitLoss;

  @JsonProperty("lspft2")
  private String thisMonthProfitLoss;

  @JsonProperty("lspft")
  private String accumulatedProfitLoss;

  @JsonProperty("tdy_lspft_rt")
  private String todayProfitRate;

  @JsonProperty("lspft_ratio")
  private String thisMonthProfitRate;

  @JsonProperty("lspft_rt")
  private String accumulatedProfitRate;

  @JsonProperty("stk_acnt_evlt_prst")
  private List<StockEvaluation> stockEvaluations;

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
  public static class StockEvaluation {
    @JsonProperty("stk_cd")
    private String stockCode;

    @JsonProperty("stk_nm")
    private String stockName;

    @JsonProperty("rmnd_qty")
    private String remainingQuantity;

    @JsonProperty("avg_prc")
    private String averagePrice;

    @JsonProperty("cur_prc")
    private String currentPrice;

    @JsonProperty("evlt_amt")
    private String evaluationAmount;

    @JsonProperty("pl_amt")
    private String profitLossAmount;

    @JsonProperty("pl_rt")
    private String profitLossRate;

    @JsonProperty("loan_dt")
    private String loanDate;

    @JsonProperty("pur_amt")
    private String purchaseAmount;

    @JsonProperty("setl_remn")
    private String settlementRemaining;

    @JsonProperty("pred_buyq")
    private String previousDayBuyQuantity;

    @JsonProperty("pred_sellq")
    private String previousDaySellQuantity;

    @JsonProperty("tdy_buyq")
    private String todayBuyQuantity;

    @JsonProperty("tdy_sellq")
    private String todaySellQuantity;
  }
}
