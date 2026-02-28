package org.sejongisc.backend.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.sejongisc.backend.attendance.entity.Attendance;
import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.sejongisc.backend.attendance.entity.AttendanceSession;

public record AttendanceResponse(
    UUID attendanceId,
    UUID userId,
    String userName,
    UUID sessionId,
    String sessionTitle,
    UUID roundId,
    String roundName,
    LocalDate roundDate,
    LocalDateTime roundStartAt,
    String roundLocation,
    String attendanceStatus,
    LocalDateTime checkedAt,
    String note,
    Double checkInLatitude,
    Double checkInLongitude,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

  public static AttendanceResponse from(Attendance attendance, AttendanceSession session, AttendanceRound round) {
    return new AttendanceResponse(
        attendance.getAttendanceId(),
        attendance.getUser() != null ? attendance.getUser().getUserId() : null,
        attendance.getUser() != null ? attendance.getUser().getName() : "익명",
        session != null ? session.getAttendanceSessionId() : null,
        session != null ? session.getTitle() : null,
        round != null ? round.getRoundId() : null,
        round != null ? round.getRoundName() : null,
        round != null ? round.getRoundDate() : null,
        round != null ? round.getStartAt() : null,
        round != null ? round.getLocationName() : null,
        attendance.getAttendanceStatus() != null ? attendance.getAttendanceStatus().name() : null,
        attendance.getCheckedAt(),
        attendance.getNote(),
        attendance.getCheckInLocation() != null ? attendance.getCheckInLocation().getLat() : null,
        attendance.getCheckInLocation() != null ? attendance.getCheckInLocation().getLng() : null,
        attendance.getCreatedDate(),
        attendance.getUpdatedDate()
    );
  }
}