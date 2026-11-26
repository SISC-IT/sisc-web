package org.sejongisc.backend.competition.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import org.sejongisc.backend.competition.entity.ParticipantStatus;

public record CompetitionParticipantResponse(
    UUID userId,
    String userName,
    String teamName,
    ParticipantStatus status,
    long tradeCount,
    LocalDateTime lastTradedAt // 거래가 없으면 null
) {
}
