package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.attendance.entity.AttendanceStatus;

import java.util.UUID;

/**
 * 회차별 출석 인원 정보
 * - 요청: GET /api/attendance/rounds/{roundId}/attendances
 * - 응답: [{userId, userName, status}, ...]
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundAttendanceResponse {

    @JsonProperty("userId")
    private UUID userId;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("status")
    private AttendanceStatus status;
}
