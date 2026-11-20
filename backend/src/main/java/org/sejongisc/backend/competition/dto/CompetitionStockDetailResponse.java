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
        String closePrice,
        String closePriceKrw,
        String exchangeRate,
        LocalDate priceDate,
        String dailyChangeRate,
        long holdingQuantity,
        String averageBuyPrice,
        String averageBuyPriceKrw,
        String investedAmountKrw,
        String valuationAmountKrw,
        String profitLossKrw,
        String holdingReturnRate,
        String cashBalance,
        String tradingFeeRate,
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
                .closePrice(Decimals.text(quote.closePrice()))
                .closePriceKrw(krw ? Decimals.text(quote.closePrice()) : null)
                .priceDate(quote.priceDate())
                .dailyChangeRate(Decimals.rateText(quote.dailyChangeRate()))
                .holdingQuantity(held ? holding.getQuantity() : 0)
                .averageBuyPrice(Decimals.text(averageBuyPrice))
                .averageBuyPriceKrw(krw ? Decimals.text(averageBuyPrice) : null)
                .investedAmountKrw(Decimals.text(invested))
                .valuationAmountKrw(Decimals.text(valuation))
                .profitLossKrw(Decimals.text(profitLoss))
                .holdingReturnRate(profitLoss != null ? Decimals.rateText(Decimals.percent(profitLoss, invested)) : null)
                .cashBalance(Decimals.text(account.getCash()))
                .tradingFeeRate(Decimals.text(competition.getFeeRate()))
                .tradable(quote.available())
                .unavailableReason(quote.unavailableReason())
                .build();
    }
}
