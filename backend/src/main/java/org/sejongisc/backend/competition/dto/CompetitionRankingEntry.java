package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CompetitionRankingEntry(
        int rank,
        Integer rankChange,
        UUID userId,
        String userName,
        String teamName,
        BigDecimal totalValue,
        BigDecimal profitRate
) {
}
