package org.sejongisc.backend.stock.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface PortfolioOverviewProjection {

    LocalDate getStartDate();

    LocalDate getEndDate();

    BigDecimal getLastTotalAsset();

    BigDecimal getInitialCapital();
}
