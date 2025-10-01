package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRequest {

    // === 출석 체크용 필드들 ===
    @Size(min = 6, max = 6, message = "출석 코드는 6자리여야 합니다")
    private String code;

    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다")
    @DecimalMax(value = "90.0", message = "위도는 90 이하이어야 합니다")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다")
    @DecimalMax(value = "180.0", message = "경도는 180 이하이어야 합니다")
    private Double longitude;

    // === 세션 생성용 필드들 (관리자) ===
    @Size(max = 100, message = "제목은 100자 이하여야 합니다")
    private String title;

    @Size(max = 50, message = "태그는 50자 이하여야 합니다")
    private String tag;

    @Future(message = "시작 시간은 현재 시간 이후여야 합니다")
    private LocalDateTime startsAt;

    @Min(value = 300, message = "최소 5분 이상이어야 합니다")
    @Max(value = 14400, message = "최대 4시간까지 설정 가능합니다")
    private Integer windowSeconds;

    @Min(value = 0, message = "포인트는 0 이상이어야 합니다")
    private Integer rewardPoints;

    // === 위치 설정용 (관리자) ===
    private Double sessionLatitude;
    private Double sessionLongitude;

    @Min(value = 1, message = "반경은 1m 이상이어야 합니다")
    @Max(value = 1000, message = "반경은 1000m 이하여야 합니다")
    private Integer radiusMeters;

    // === 공통 필드들 ===
    private UUID sessionId;
    private String note;
    private String deviceInfo;
    private String visibility; // PUBLIC, PRIVATE
}
