package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.Market;

@Builder
public record CompetitionStockDetailResponse(
        String ticker,
        String companyName,
        Market market,
        String currency,
        UUID closePriceId,
        BigDecimal closePrice,
        BigDecimal closePriceKrw,
        BigDecimal exchangeRate,
        LocalDate priceDate,
        BigDecimal dailyChangeRate,
        long holdingQuantity,
        BigDecimal averageBuyPrice,
        BigDecimal averageBuyPriceKrw,
        BigDecimal investedAmountKrw,
        BigDecimal valuationAmountKrw,
        BigDecimal profitLossKrw,
        BigDecimal holdingReturnRate,
        BigDecimal cashBalance,
        BigDecimal tradingFeeRate,
        boolean tradable,
        UnavailableReason unavailableReason
) {

    public static CompetitionStockDetailResponse of(
            Competition competition,
            CompetitionStock stock,
            StockQuote quote,
            CompetitionAccount account,
            Holding holding,
            UnavailableReason reason
    ) {
        boolean krw = stock.getMarket() == Market.KR;
        boolean held = holding != null;
        BigDecimal quantity = held ? BigDecimal.valueOf(holding.getQuantity()) : null;
        BigDecimal averageBuyPrice = held ? holding.getAvgBuyPrice() : null;
        BigDecimal invested = held && krw ? averageBuyPrice.multiply(quantity) : null;
        BigDecimal valuation = invested != null && quote.available() ? quote.closePrice().multiply(quantity) : null;
        BigDecimal profitLoss = valuation != null ? valuation.subtract(invested) : null;
        BigDecimal holdingReturnRate = profitLoss != null
                ? profitLoss.divide(invested, 6, RoundingMode.HALF_UP)
                : null;

        return CompetitionStockDetailResponse.builder()
                .ticker(stock.getTicker())
                .companyName(stock.getName())
                .market(stock.getMarket())
                .currency(stock.getMarket().getCurrency())
                .closePriceId(quote.closePriceId())
                .closePrice(quote.closePrice())
                .closePriceKrw(krw ? quote.closePrice() : null)
                .priceDate(quote.priceDate())
                .dailyChangeRate(quote.dailyChangeRate())
                .holdingQuantity(held ? holding.getQuantity() : 0)
                .averageBuyPrice(averageBuyPrice)
                .averageBuyPriceKrw(krw ? averageBuyPrice : null)
                .investedAmountKrw(invested)
                .valuationAmountKrw(valuation)
                .profitLossKrw(profitLoss)
                .holdingReturnRate(holdingReturnRate)
                .cashBalance(account.getCash())
                .tradingFeeRate(competition.getFeeRate())
                .tradable(reason == null)
                .unavailableReason(reason)
                .build();
    }
}
