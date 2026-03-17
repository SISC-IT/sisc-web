package org.sejongisc.backend.attendance.dto.sessionUser;

import java.util.List;
import java.util.UUID;

public record UserAttendanceRowResponse(
    UUID userId,
    String userName,
    String studentId, // 학번 추가 필요
    String role,      // "관리자" 또는 "일반" (SessionRole 기반 변환)
    List<AttendanceStatusResponse> attendances // 각 회차별 상태 (순서대로)
) {}