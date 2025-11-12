package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(
        title = "출석 체크인 응답",
        description = "출석 체크인 요청에 대한 응답. 체크인 결과, 출석 상태, 실패 사유 등을 포함합니다."
)
public class AttendanceCheckInResponse {

    @Schema(
            description = "체크인한 라운드의 고유 ID",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    private UUID roundId;

    @Schema(
            description = "출석 체크인 성공 여부",
            example = "true"
    )
    private Boolean success;

    @Schema(
            description = "출석 상태. PRESENT(정시 출석), LATE(지각), ABSENT(결석)",
            example = "PRESENT"
    )
    private String status;

    @Schema(
            description = "체크인 실패 사유. 실패한 경우에만 값이 있습니다. " +
                    "예: '시간 초과', '위치 불일치', '중복 출석' 등",
            example = "시간 초과",
            nullable = true
    )
    private String failureReason;

    @Schema(
            description = "실제 출석 체크인된 시간",
            example = "2025-11-06 10:15:30",
            type = "string",
            format = "date-time"
    )
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkedAt;

    @Schema(
            description = "출석 성공 시 지급된 포인트. 실패한 경우 null",
            example = "10",
            nullable = true
    )
    private Integer awardedPoints;

    @Schema(
            description = "현재부터 라운드 출석 마감까지 남은 시간 (초 단위). 음수이면 마감 종료",
            example = "1234"
    )
    private Long remainingSeconds;
}
