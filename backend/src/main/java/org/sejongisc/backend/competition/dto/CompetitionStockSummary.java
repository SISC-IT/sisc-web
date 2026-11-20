package org.sejongisc.backend.competition.dto;

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
        String closePrice,
        String closePriceKrw,
        String previousClosePrice,
        String dailyChangeRate,
        LocalDate priceDate,
        boolean tradable,
        UnavailableReason unavailableReason
) {

    public static CompetitionStockSummary of(CompetitionStock stock, StockQuote quote) {
        String closePrice = Decimals.text(quote.closePrice());
        return new CompetitionStockSummary(
                stock.getTicker(),
                stock.getName(),
                stock.getMarket(),
                stock.getMarket().getCurrency(),
                quote.closePriceId(),
                closePrice,
                stock.getMarket() == Market.KR ? closePrice : null,
                Decimals.text(quote.previousClosePrice()),
                Decimals.rateText(quote.dailyChangeRate()),
                quote.priceDate(),
                quote.available(),
                quote.unavailableReason()
        );
    }
}
