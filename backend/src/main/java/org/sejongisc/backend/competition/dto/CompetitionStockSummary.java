package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Market;

public record CompetitionStockSummary(
        String ticker,
        String companyName,
        Market market,
        String currency,
        UUID closePriceId,
        BigDecimal closePrice,
        BigDecimal closePriceKrw,
        BigDecimal previousClosePrice,
        BigDecimal dailyChangeRate,
        LocalDate priceDate,
        boolean tradable,
        UnavailableReason unavailableReason
) {

    public static CompetitionStockSummary of(CompetitionStock stock, StockQuote quote, UnavailableReason reason) {
        return new CompetitionStockSummary(
                stock.getTicker(),
                stock.getName(),
                stock.getMarket(),
                stock.getMarket().getCurrency(),
                quote.closePriceId(),
                quote.closePrice(),
                stock.getMarket() == Market.KR ? quote.closePrice() : null,
                quote.previousClosePrice(),
                quote.dailyChangeRate(),
                quote.priceDate(),
                reason == null,
                reason
        );
    }
}
