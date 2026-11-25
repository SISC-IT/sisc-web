package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;

public record CompetitionHoldingResponse(
        String ticker,
        String companyName,
        long quantity,
        BigDecimal avgBuyPrice,
        BigDecimal closePrice,
        LocalDate priceDate,
        BigDecimal evaluationAmount,
        BigDecimal profit,
        BigDecimal profitRate
) {

    public static CompetitionHoldingResponse of(Holding holding, DailyClosePrice latest) {
        String ticker = holding.getStock().getTicker();
        String companyName = holding.getStock().getName();
        long quantity = holding.getQuantity();
        if (latest == null) {
            return new CompetitionHoldingResponse(
                    ticker, companyName, quantity, holding.getAvgBuyPrice(), null, null, null, null, null);
        }

        BigDecimal shares = BigDecimal.valueOf(quantity);
        BigDecimal cost = holding.getAvgBuyPrice().multiply(shares);
        BigDecimal evaluationAmount = latest.getClosePrice().multiply(shares);
        BigDecimal profit = evaluationAmount.subtract(cost);
        return new CompetitionHoldingResponse(
                ticker,
                companyName,
                quantity,
                holding.getAvgBuyPrice(),
                latest.getClosePrice(),
                latest.getPriceDate(),
                evaluationAmount,
                profit,
                profit.divide(cost, 6, RoundingMode.HALF_UP));
    }
}
