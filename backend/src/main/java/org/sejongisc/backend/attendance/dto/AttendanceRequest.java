package org.sejongisc.backend.attendance.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRequest {

    // === 출석 체크용 필드들 ===
    @NotBlank(message = "출석 코드는 필수입니다")
    @Size(min = 6, max = 6, message = "출석 코드는 6자리여야 합니다")
    private String code;

    @NotNull(message = "위도는 필수입니다")
    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다")
    @DecimalMax(value = "90.0", message = "위도는 90 이하이어야 합니다")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다")
    @DecimalMax(value = "180.0", message = "경도는 180 이하이어야 합니다")
    private Double longitude;

    @Size(max = 500, message = "메모는 500자 이하여야 합니다")
    private String note;

    @Size(max = 200, message = "디바이스 정보는 200자 이하여야 합니다")
    private String deviceInfo;

}
