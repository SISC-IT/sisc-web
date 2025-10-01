package org.sejongisc.backend.attendance.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.sejongisc.backend.attendance.entity.SessionVisibility;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSessionRequest {

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다")
    private String title;

    @Size(max = 50, message = "태그는 50자 이하여야 합니다")
    private String tag;

    @NotNull(message = "시작 시간은 필수입니다")
    @Future(message = "시작 시간은 현재 시간 이후여야 합니다")
    private LocalDateTime startsAt;

    @Min(value = 300, message = "최소 5분 이상이어야 합니다")
    @Max(value = 14400, message = "최대 4시간 설정 가능합니다")
    private Integer windowSeconds;

    @Min(value = 0, message = "포인트는 0 이상이어야 합니다")
    private Integer rewardPoints;

    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다")
    @DecimalMax(value = "90.0", message = "위도는 90 이하이어야 합니다")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다")
    @DecimalMax(value = "180.0", message = "경도는 180 이하이어야 합니다")
    private Double longitude;

    @Min(value = 1, message = "반경은 1m 이상이어야 합니다")
    @Max(value = 500, message = "반경은 500m 이하여야 합니다")
    private Integer radiusMeters;

    private SessionVisibility visibility;
}

