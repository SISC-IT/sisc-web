package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.ParticipantStatus;

public record CompetitionResponse(
    UUID competitionId,
    String name,
    CompetitionStatus status,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal initialCash,
    BigDecimal feeRate,
    BigDecimal depositInterestRate,
    ParticipantStatus myStatus // 참가자가 아니면 null
) {
  public static CompetitionResponse of(Competition competition, ParticipantStatus myStatus) {
    return new CompetitionResponse(
        competition.getCompetitionId(),
        competition.getName(),
        competition.getStatus(),
        competition.getStartDate(),
        competition.getEndDate(),
        competition.getInitialCash(),
        competition.getFeeRate(),
        competition.getDepositInterestRate(),
        myStatus
    );
  }
}
