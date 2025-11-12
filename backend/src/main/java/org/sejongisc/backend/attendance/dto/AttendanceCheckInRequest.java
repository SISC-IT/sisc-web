package org.sejongisc.backend.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 출석 체크인 요청
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        title = "출석 체크인 요청",
        description = "라운드에 출석 체크인을 기록할 때 사용하는 요청 객체. " +
                "라운드 ID, 현재 위치(GPS), 사용자 이름을 포함합니다."
)
public class AttendanceCheckInRequest {

    @NotNull(message = "라운드 ID는 필수입니다")
    @Schema(
            description = "체크인할 라운드의 고유 ID",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    private UUID roundId;

    @NotNull(message = "위도는 필수입니다")
    @DecimalMin(value = "-90.0", message = "위도는 -90도 이상이어야 합니다")
    @DecimalMax(value = "90.0", message = "위도는 90도 이하여야 합니다")
    @Schema(
            description = "현재 사용자의 위치 위도 (WGS84 좌표계)",
            example = "37.4979",
            minimum = "-90.0",
            maximum = "90.0"
    )
    private Double latitude;

    @NotNull(message = "경도는 필수입니다")
    @DecimalMin(value = "-180.0", message = "경도는 -180도 이상이어야 합니다")
    @DecimalMax(value = "180.0", message = "경도는 180도 이하여야 합니다")
    @Schema(
            description = "현재 사용자의 위치 경도 (WGS84 좌표계)",
            example = "127.0276",
            minimum = "-180.0",
            maximum = "180.0"
    )
    private Double longitude;

    @Schema(
            description = "익명 사용자의 이름 (선택사항). 입력하지 않으면 '익명사용자-{UUID}'로 자동 생성됨.",
            example = "김철수",
            nullable = true
    )
    private String userName;
}
