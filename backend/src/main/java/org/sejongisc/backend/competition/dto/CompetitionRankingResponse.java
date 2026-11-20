package org.sejongisc.backend.competition.dto;

import java.time.LocalDate;
import java.util.List;

public record CompetitionRankingResponse(
        LocalDate snapshotDate,
        List<CompetitionRankingEntry> rankings
) {
}
