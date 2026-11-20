package org.sejongisc.backend.competition.dto;

import java.util.List;

public record CompetitionStockListResponse(
        int page,
        int size,
        long totalCount,
        String exchangeRate,
        List<CompetitionStockSummary> stocks
) {
}
