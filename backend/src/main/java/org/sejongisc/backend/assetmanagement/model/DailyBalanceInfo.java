package org.sejongisc.backend.assetmanagement.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DailyBalanceInfo {
  private String date;
  private String totalBuyAmount;
  private String totalEvaluationAmount;
  private String totalEvaluationProfit;
  private String totalProfitRate;
  private String depositBalance;
  private String dailyStockAsset;
  private String buyWeight;
  private List<StockItem> dailyBalanceRate;

  @Getter
  @Builder
  @AllArgsConstructor
  public static class StockItem {
    private String currentPrice;
    private String stockCode;
    private String stockName;
    private String remainderQuantity;
    private String buyUnitValue;
    private String buyWeight;
    private String evaluationProfit;
    private String profitRate;
    private String evaluationAmount;
    private String evaluationWeight;
  }
}
