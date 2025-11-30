package org.sejongisc.backend.stock.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface PortfolioSimpleProjection {
    BigDecimal getTotalAsset();
    LocalDate getCreatedAt();
}
