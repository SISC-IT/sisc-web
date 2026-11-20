package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.util.List;

public record CompetitionStockListResponse(
        int page,
        int size,
        long totalCount,
        BigDecimal exchangeRate,
        List<CompetitionStockSummary> stocks
) {
}
