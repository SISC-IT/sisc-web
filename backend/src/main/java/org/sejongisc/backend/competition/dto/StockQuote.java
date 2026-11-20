package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.sejongisc.backend.competition.entity.DailyClosePrice;

public record StockQuote(DailyClosePrice latest, DailyClosePrice previous) {

    public static StockQuote of(List<DailyClosePrice> latestTwo) {
        return new StockQuote(
                latestTwo.isEmpty() ? null : latestTwo.get(0),
                latestTwo.size() < 2 ? null : latestTwo.get(1));
    }

    public boolean available() {
        return latest != null;
    }

    public UUID closePriceId() {
        return available() ? latest.getClosePriceId() : null;
    }

    public BigDecimal closePrice() {
        return available() ? latest.getClosePrice() : null;
    }

    public LocalDate priceDate() {
        return available() ? latest.getPriceDate() : null;
    }

    public BigDecimal previousClosePrice() {
        return previous == null ? null : previous.getClosePrice();
    }

    public BigDecimal dailyChangeRate() {
        if (previous == null) {
            return null;
        }
        return latest.getClosePrice().subtract(previous.getClosePrice())
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.getClosePrice(), 6, RoundingMode.HALF_UP);
    }
}
