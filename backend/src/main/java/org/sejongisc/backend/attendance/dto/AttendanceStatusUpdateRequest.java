package org.sejongisc.backend.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 출석 상태 수정 요청
 * - POST /api/attendance/rounds/{roundId}/attendances/{userId}
 * - body: {status, reason}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStatusUpdateRequest {

    @NotBlank(message = "출석 상태는 필수입니다 (PRESENT, LATE, ABSENT, EXCUSED)")
    private String status;

    private String reason;
}
