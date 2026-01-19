package org.sejongisc.backend.attendance.dto;


import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceRoundRequest(
    LocalDate roundDate,
    LocalDateTime startAt,
    LocalDateTime closeAt,      // 선택(없으면 null로 받고 서버가 자동 계산해도 됨)
    String roundName,
    String locationName
) {


}
