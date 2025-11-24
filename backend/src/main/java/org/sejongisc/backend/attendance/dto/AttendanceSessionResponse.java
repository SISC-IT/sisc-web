package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        title = "출석 세션 응답",
        description = "출석 세션의 상세 정보. 세션 설정, 기본 시간, 위치 등을 포함합니다."
)
public class AttendanceSessionResponse {

    @Schema(
            description = "출석 세션의 고유 ID",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    private UUID attendanceSessionId;

    @Schema(
            description = "세션의 제목/이름",
            example = "금융 IT팀 세션"
    )
    private String title;

    @Schema(
            description = "세션 개최 위치 정보",
            example = "{\"lat\": 37.5499, \"lng\": 127.0751}"
    )
    private LocationInfo location;

    @Schema(
            description = "세션의 기본 시작 시간",
            example = "18:30:00"
    )
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime defaultStartTime;

    @Schema(
            description = "출석 인정 시간 (분 단위)",
            example = "30"
    )
    private Integer defaultAvailableMinutes;

    @Schema(
            description = "출석 완료 시 지급할 포인트",
            example = "100"
    )
    private Integer rewardPoints;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        @Schema(description = "위도", example = "37.5499")
        private Double lat;

        @Schema(description = "경도", example = "127.0751")
        private Double lng;
    }
}
