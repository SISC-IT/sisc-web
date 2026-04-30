package org.sejongisc.backend.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeLogDto(
    Long id,
    Long xaiReportId,
    String ticker,
    String displayTicker,
    LocalDate fillDate,
    BigDecimal fillPrice,
    Integer qty,
    String side,
    BigDecimal value,
    Integer positionQty,
    BigDecimal avgPrice,
    BigDecimal pnlRealized
) {
}
