package org.sejongisc.backend.assetmanagement.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AccountEvaluationInfo {
  private String accountName;
  private String branchName;
  private String deposit;
  private String d2Deposit;
  private String totalEstimatedAmount;
  private String assetEvaluationAmount;
  private String totalPurchaseAmount;
  private String presumedDepositAssetAmount;
  private String totalGuaranteeSellAmount;
  private String todayInvestmentPrincipal;
  private String thisMonthInvestmentPrincipal;
  private String accumulatedInvestmentPrincipal;
  private String todayProfitLoss;
  private String thisMonthProfitLoss;
  private String accumulatedProfitLoss;
  private String todayProfitRate;
  private String thisMonthProfitRate;
  private String accumulatedProfitRate;
  private List<StockEvaluation> stockEvaluations;

  @Getter
  @Builder
  @AllArgsConstructor
  public static class StockEvaluation {
    private String stockCode;
    private String stockName;
    private String remainingQuantity;
    private String averagePrice;
    private String currentPrice;
    private String evaluationAmount;
    private String profitLossAmount;
    private String profitLossRate;
    private String loanDate;
    private String purchaseAmount;
    private String settlementRemaining;
    private String previousDayBuyQuantity;
    private String previousDaySellQuantity;
    private String todayBuyQuantity;
    private String todaySellQuantity;
  }
}
