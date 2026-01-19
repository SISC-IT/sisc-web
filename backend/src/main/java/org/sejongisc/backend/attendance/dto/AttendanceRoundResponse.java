package org.sejongisc.backend.attendance.dto;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.sejongisc.backend.attendance.entity.AttendanceRound;

public record AttendanceRoundResponse(
    UUID roundId,
    UUID sessionId,
    LocalDate roundDate,
    LocalDateTime startAt,
    LocalDateTime closeAt,
    String roundStatus,
    String roundName,
    String locationName
) {
    public static AttendanceRoundResponse from(AttendanceRound round) {
        return from(round, false);
    }
    public static AttendanceRoundResponse from(AttendanceRound round, boolean includeQr) {
        return new AttendanceRoundResponse(
            round.getRoundId(),
            round.getAttendanceSession().getAttendanceSessionId(),
            round.getRoundDate(),
            round.getStartAt(),
            round.getCloseAt(),
            round.getRoundStatus().name(),
            round.getRoundName(),
            round.getLocationName()
        );
    }
}

