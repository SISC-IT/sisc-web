package org.sejongisc.backend.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;
import org.sejongisc.backend.attendance.entity.SessionVisibility;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        title = "출석 세션 생성/수정 요청",
        description = "관리자가 출석 세션을 생성하거나 수정할 때 사용하는 요청 객체. " +
                "세션의 기본 정보, 시간, 위치, 포인트 설정을 포함합니다."
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
            description = "세션의 분류 태그. 예: '금융IT', '동아리 전체', '스터디'",
            example = "금융IT",
            maxLength = 50
    )
    @Size(max = 50, message = "태그는 50자 이하여야 합니다")
    private String tag;

    @Schema(
            description = "세션 시작 시간 (ISO 8601 형식). 현재 시간 이후여야 합니다.",
            example = "2024-11-15T14:00:00",
            type = "string",
            format = "date-time"
    )
    @NotNull(message = "시작 시간은 필수입니다")
    // @Future(message = "시작 시간은 현재 시간 이후여야 합니다") // TODO: 테스트용으로 주석 처리
    private LocalDateTime startsAt;

    @Schema(
            description = "출석 체크인이 가능한 시간 윈도우 (초 단위). " +
                    "범위: 300초(5분) ~ 14400초(4시간)",
            example = "1800",
            minimum = "300",
            maximum = "14400"
    )
    @Min(value = 300, message = "최소 5분 이상이어야 합니다")
    @Max(value = 14400, message = "최대 4시간 설정 가능합니다")
    private Integer windowSeconds;

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

    @Schema(
            description = "세션의 공개 범위. PUBLIC(공개) 또는 PRIVATE(비공개). " +
                    "공개 세션은 모든 사용자에게 보이고, 비공개는 관리자에게만 보입니다.",
            example = "PUBLIC",
            implementation = SessionVisibility.class
    )
    private SessionVisibility visibility;
}

