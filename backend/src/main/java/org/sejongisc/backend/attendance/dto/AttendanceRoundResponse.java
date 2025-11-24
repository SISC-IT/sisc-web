package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.attendance.entity.AttendanceRound;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        title = "출석 라운드 응답",
        description = "출석 라운드의 상세 정보. 라운드 날짜, 시간, 상태를 포함합니다."
)
public class AttendanceRoundResponse {

    @Schema(
            description = "라운드의 고유 ID",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    private UUID roundId;

    @Schema(
            description = "라운드 진행 날짜",
            example = "2025-11-06",
            type = "string",
            format = "date"
    )
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate roundDate;

    @Schema(
            description = "라운드 출석 시작 시간",
            example = "10:00:00",
            type = "string",
            format = "time"
    )
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    @Schema(
            description = "출석 가능한 시간 (분단위)",
            example = "20"
    )
    private Integer availableMinutes;

    @Schema(
            description = "라운드의 현재 상태. (opened, upcoming, closed 등)",
            example = "opened"
    )
    private String status;

    /**
     * 엔티티를 DTO로 변환
     * status는 실시간으로 계산되어 반환됨
     */
    public static AttendanceRoundResponse fromEntity(AttendanceRound round) {
        // 현재 시간 기준으로 라운드 상태를 실시간 계산
        // RoundStatus.getValue()를 사용하여 명시적이고 안전한 변환
        String statusString = round.calculateCurrentStatus().getValue();

        return AttendanceRoundResponse.builder()
                .roundId(round.getRoundId())
                .roundDate(round.getRoundDate())
                .startTime(round.getStartTime())
                .availableMinutes(round.getAllowedMinutes())
                .status(statusString)
                .build();
    }
}
