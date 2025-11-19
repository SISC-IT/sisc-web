package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 출석 체크인 응답
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCheckInResponse {

    private UUID roundId;              // 라운드 ID

    private Boolean success;            // 출석 성공 여부

    private String status;              // 출석 상태 (PRESENT, LATE, ABSENT)

    private String failureReason;       // 실패 사유 (시간초과, 위치 불일치, 등)

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkedAt;    // 출석 시간

    private Integer awardedPoints;      // 지급된 포인트

    private Long remainingSeconds;      // 남은 체크인 시간 (초단위)
}
