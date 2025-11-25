package org.sejongisc.backend.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        title = "출석 라운드 생성/수정 요청",
        description = "출석 라운드를 생성하거나 수정할 때 사용하는 요청 객체. " +
                "라운드의 날짜, 시작 시간, 출석 가능한 시간을 설정합니다."
)
public class AttendanceRoundRequest {

    @NotNull(message = "세션 ID는 필수입니다")
    @Schema(
            description = "회차가 속할 세션의 ID",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            type = "string",
            format = "uuid"
    )
    private UUID sessionId;

    @Schema(
            description = "라운드 진행 날짜 (YYYY-MM-DD 형식)",
            example = "2025-11-06",
            type = "string",
            format = "date"
    )
    private LocalDate date;

    @NotNull(message = "시작 시간은 필수입니다")
    @Schema(
            description = "라운드 출석 시작 시간 (HH:mm:ss 형식). 이 시간부터 출석 체크인이 가능합니다.",
            example = "10:00:00",
            type = "string",
            format = "time"
    )
    private LocalTime startTime;

    @NotNull(message = "출석 가능 시간은 필수입니다")
    @Min(value = 1, message = "출석 가능 시간은 최소 1분 이상이어야 합니다")
    @Max(value = 120, message = "출석 가능 시간은 최대 120분 이하여야 합니다")
    @Schema(
            description = "출석 가능한 시간 (분단위). 시작 시간으로부터 이 시간 동안 출석을 기록할 수 있습니다. " +
                    "범위: 1분 ~ 120분",
            example = "30",
            minimum = "1",
            maximum = "120"
    )
    private Integer availableMinutes;
}
