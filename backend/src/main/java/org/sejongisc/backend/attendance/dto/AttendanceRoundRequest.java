package org.sejongisc.backend.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRoundRequest {

    private LocalDate roundDate;           // 라운드 날짜 (예: 2025-11-06)

    private LocalTime startTime;           // 출석 시작 시간 (예: 10:00)

    private Integer allowedMinutes;        // 출석 인정 시간 (분단위, 예: 30)
}
