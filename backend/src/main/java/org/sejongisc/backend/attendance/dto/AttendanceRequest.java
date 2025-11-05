package org.sejongisc.backend.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    title = "출석 체크인 요청",
    description = "학생이 출석 체크인 시 제출하는 요청 객체. 출석 코드, GPS 위치 정보를 포함합니다."
)
public class AttendanceRequest {

    // === 출석 체크용 필드들 ===
    @Schema(
            description = "출석 세션의 6자리 코드. 관리자가 생성한 코드를 입력합니다.",
            example = "ABC123",
            minLength = 6,
            maxLength = 6
    )
    @NotBlank(message = "출석 코드는 필수입니다")
    @Size(min = 6, max = 6, message = "출석 코드는 6자리여야 합니다")
    private String code;

    @Schema(
            description = "체크인 위치의 위도 (latitude). WGS84 좌표계 사용. 범위: -90 ~ 90",
            example = "37.4979",
            minimum = "-90.0",
            maximum = "90.0"
    )
    @NotNull(message = "위도는 필수입니다")
    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다")
    @DecimalMax(value = "90.0", message = "위도는 90 이하이어야 합니다")
    private Double latitude;

    @Schema(
            description = "체크인 위치의 경도 (longitude). WGS84 좌표계 사용. 범위: -180 ~ 180",
            example = "127.0276",
            minimum = "-180.0",
            maximum = "180.0"
    )
    @NotNull(message = "경도는 필수입니다")
    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다")
    @DecimalMax(value = "180.0", message = "경도는 180 이하이어야 합니다")
    private Double longitude;

    @Schema(
            description = "체크인 시 추가 메모. 선택 사항입니다.",
            example = "교실 앞에서 체크인",
            maxLength = 500
    )
    @Size(max = 500, message = "메모는 500자 이하여야 합니다")
    private String note;

    @Schema(
            description = "체크인에 사용한 디바이스 정보. 예: 'iPhone 12', 'Android Pixel 6' 등",
            example = "iPhone 14 Pro",
            maxLength = 200
    )
    @Size(max = 200, message = "디바이스 정보는 200자 이하여야 합니다")
    private String deviceInfo;

}
