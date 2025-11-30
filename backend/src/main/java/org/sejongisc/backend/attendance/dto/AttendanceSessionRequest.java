package org.sejongisc.backend.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        title = "출석 세션 생성/수정 요청",
        description = "관리자가 출석 세션을 생성하거나 수정할 때 사용하는 요청 객체. " +
                "세션의 기본 정보, 기본 시간, 위치, 포인트 설정을 포함합니다."
)
public class AttendanceSessionRequest {

    @Schema(
            description = "세션의 제목/이름",
            example = "2024년 10월 동아리 정기 모임",
            maxLength = 100
    )
    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다")
    private String title;

    @Schema(
            description = "세션의 기본 시작 시간 (HH:mm:ss 형식). 시간 단위만 지정합니다.",
            example = "18:30:00",
            type = "string",
            pattern = "HH:mm:ss"
    )
    @NotNull(message = "기본 시작 시간은 필수입니다")
    private LocalTime defaultStartTime;

    @Schema(
            description = "출석 인정 시간 (분 단위). " +
                    "범위: 5분 ~ 240분(4시간)",
            example = "30",
            minimum = "5",
            maximum = "240"
    )
    @Min(value = 5, message = "최소 5분 이상이어야 합니다")
    @Max(value = 240, message = "최대 4시간 설정 가능합니다")
    private Integer allowedMinutes;

    @Schema(
            description = "출석 완료 시 지급할 포인트",
            example = "10",
            minimum = "0"
    )
    @Min(value = 0, message = "포인트는 0 이상이어야 합니다")
    private Integer rewardPoints;

    @Schema(
            description = "세션 개최 위치의 위도 (latitude). WGS84 좌표계. 선택 사항",
            example = "37.4979",
            minimum = "-90.0",
            maximum = "90.0"
    )
    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다")
    @DecimalMax(value = "90.0", message = "위도는 90 이하이어야 합니다")
    private Double latitude;

    @Schema(
            description = "세션 개최 위치의 경도 (longitude). WGS84 좌표계. 선택 사항",
            example = "127.0276",
            minimum = "-180.0",
            maximum = "180.0"
    )
    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다")
    @DecimalMax(value = "180.0", message = "경도는 180 이하이어야 합니다")
    private Double longitude;

    @Schema(
            description = "GPS 기반 위치 검증을 위한 반경 (미터 단위). " +
                    "지정된 위치에서 이 반경 내에 있어야만 체크인이 가능합니다. " +
                    "범위: 1m ~ 500m",
            example = "100",
            minimum = "1",
            maximum = "500"
    )
    @Min(value = 1, message = "반경은 1m 이상이어야 합니다")
    @Max(value = 500, message = "반경은 500m 이하여야 합니다")
    private Integer radiusMeters;

}

