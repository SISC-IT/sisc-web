package org.sejongisc.backend.attendance.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import org.sejongisc.backend.attendance.entity.Attendance;

public record AttendanceResponse(
    UUID attendanceId,
    UUID userId,
    String userName,
    UUID roundId,
    String attendanceStatus,
    LocalDateTime checkedAt,
    String note,
    Double checkInLatitude,
    Double checkInLongitude,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AttendanceResponse from(Attendance a) {
        return new AttendanceResponse(
            a.getAttendanceId(),
            a.getUser() != null ? a.getUser().getUserId() : null,
            a.getUser() != null ? a.getUser().getName() : "익명",
            a.getAttendanceRound() != null ? a.getAttendanceRound().getRoundId() : null,
            a.getAttendanceStatus() != null ? a.getAttendanceStatus().name() : null,
            a.getCheckedAt(),
            a.getNote(),
            a.getCheckInLocation() != null ? a.getCheckInLocation().getLat() : null,
            a.getCheckInLocation() != null ? a.getCheckInLocation().getLng() : null,
            a.getCreatedDate(),
            a.getUpdatedDate()
        );
    }
}