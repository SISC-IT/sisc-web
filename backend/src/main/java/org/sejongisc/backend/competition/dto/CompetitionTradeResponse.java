package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.sejongisc.backend.competition.entity.Trade;
import org.sejongisc.backend.competition.entity.TradeSide;

public record CompetitionTradeResponse(
        UUID tradeId,
        String ticker,
        TradeSide side,
        long quantity,
        BigDecimal price,
        BigDecimal fee,
        LocalDateTime executedAt,
        BigDecimal cashBalance
) {

    public static CompetitionTradeResponse of(Trade trade) {
        return new CompetitionTradeResponse(
                trade.getTradeId(),
                trade.getStock().getTicker(),
                trade.getSide(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getFee(),
                trade.getExecutedAt(),
                trade.getCashAfter()
        );
    }
}
