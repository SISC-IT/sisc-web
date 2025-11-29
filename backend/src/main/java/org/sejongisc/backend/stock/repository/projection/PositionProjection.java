package org.sejongisc.backend.stock.repository.projection;

import java.math.BigDecimal;

public interface PositionProjection {

    String getTicker();
    Integer getPositionQty();
    BigDecimal getAvgPrice();
    BigDecimal getCurrentPrice();
    BigDecimal getMarketPrice();
}
