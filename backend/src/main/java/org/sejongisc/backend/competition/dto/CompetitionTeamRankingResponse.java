package org.sejongisc.backend.competition.dto;

import java.time.LocalDate;
import java.util.List;

public record CompetitionTeamRankingResponse(
        LocalDate snapshotDate,
        List<CompetitionTeamRankingEntry> rankings
) {
}
