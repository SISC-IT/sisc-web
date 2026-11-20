package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
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
            Holding holding
    ) {
        boolean krw = stock.getMarket() == Market.KR;
        boolean held = holding != null;
        BigDecimal quantity = held ? BigDecimal.valueOf(holding.getQuantity()) : null;
        BigDecimal averageBuyPrice = held ? holding.getAvgBuyPrice() : null;
        BigDecimal invested = held && krw ? averageBuyPrice.multiply(quantity) : null;
        BigDecimal valuation = invested != null && quote.available() ? quote.closePrice().multiply(quantity) : null;
        BigDecimal profitLoss = valuation != null ? valuation.subtract(invested) : null;

        return CompetitionStockDetailResponse.builder()
                .ticker(stock.getTicker())
                .companyName(stock.getName())
                .market(stock.getMarket())
                .currency(stock.getMarket().getCurrency())
                .closePriceId(quote.closePriceId())
                .closePrice(Decimals.plain(quote.closePrice()))
                .closePriceKrw(krw ? Decimals.plain(quote.closePrice()) : null)
                .priceDate(quote.priceDate())
                .dailyChangeRate(Decimals.plain(quote.dailyChangeRate()))
                .holdingQuantity(held ? holding.getQuantity() : 0)
                .averageBuyPrice(Decimals.plain(averageBuyPrice))
                .averageBuyPriceKrw(krw ? Decimals.plain(averageBuyPrice) : null)
                .investedAmountKrw(Decimals.plain(invested))
                .valuationAmountKrw(Decimals.plain(valuation))
                .profitLossKrw(Decimals.plain(profitLoss))
                .holdingReturnRate(profitLoss != null ? Decimals.plain(Decimals.percent(profitLoss, invested)) : null)
                .cashBalance(Decimals.plain(account.getCash()))
                .tradingFeeRate(Decimals.plain(competition.getFeeRate()))
                .tradable(quote.available())
                .unavailableReason(quote.unavailableReason())
                .build();
    }
}
