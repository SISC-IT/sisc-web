package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;

public record CompetitionTeamRankingEntry(
        int rank,
        String teamName,
        BigDecimal averageRank,
        int rankSum,
        int memberCount
) {
}
