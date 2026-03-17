package org.sejongisc.backend.attendance.dto.sessionUser;

import java.util.UUID;

public record AttendanceStatusResponse(
    UUID roundId,
    String status, // PRESENT, LATE, ABSENT, etc.
    UUID attendanceId // 수정 시 필요할 수 있음
) {}