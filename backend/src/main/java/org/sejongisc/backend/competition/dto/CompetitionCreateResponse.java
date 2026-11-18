package org.sejongisc.backend.competition.dto;

import org.sejongisc.backend.competition.entity.Competition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CompetitionCreateResponse(
    UUID competitionId,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal initialCash,
    BigDecimal feeRate,
    BigDecimal depositInterestRate,
    int participantCount
) {
  public static CompetitionCreateResponse of(Competition competition, int participantCount) {
    return new CompetitionCreateResponse(
        competition.getCompetitionId(),
        competition.getName(),
        competition.getStartDate(),
        competition.getEndDate(),
        competition.getInitialCash(),
        competition.getFeeRate(),
        competition.getDepositInterestRate(),
        participantCount
    );
  }
}
