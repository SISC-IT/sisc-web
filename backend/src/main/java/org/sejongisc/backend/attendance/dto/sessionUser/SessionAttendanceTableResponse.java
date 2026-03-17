package org.sejongisc.backend.attendance.dto.sessionUser;

import java.util.List;

public record SessionAttendanceTableResponse(
    String sessionTitle,
    List<RoundHeaderResponse> rounds, // 테이블 헤더용 (1회차, 2회차...)
    List<UserAttendanceRowResponse> userRows // 테이블 바디용 (유저별 행)
) {}