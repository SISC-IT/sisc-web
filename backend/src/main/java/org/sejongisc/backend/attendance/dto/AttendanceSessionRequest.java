package org.sejongisc.backend.attendance.dto;

import org.sejongisc.backend.attendance.entity.AttendanceSession;

/**
 * 출석 세션 생성/수정 요청 DTO
 */

public record AttendanceSessionRequest(
    String title,
    String description,
    Integer allowedMinutes,
    String status
) {
  public static AttendanceSessionRequest from(AttendanceSession session) {
    return new AttendanceSessionRequest(
        session.getTitle(),
        session.getDescription(),
        session.getAllowedMinutes(),
        session.getStatus().name()
    );
  }
}



