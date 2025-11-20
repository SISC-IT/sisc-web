package org.sejongisc.backend.stock.dto;

import java.math.BigDecimal;

public record HoldingDto(
    String ticker,
    Integer positionQty,
    BigDecimal pnlUnrealized,
    BigDecimal cashAfter
) {
}
