package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.sejongisc.backend.attendance.entity.AttendanceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        title = "출석 정보 응답",
        description = "학생의 출석 기록 정보. 체크인 시간, 출석 상태, 포인트, GPS 정보 등을 포함합니다."
)
public class AttendanceResponse {

    @Schema(
            description = "출석 기록의 고유 ID",
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    private UUID attendanceId;

    @Schema(
            description = "체크인한 학생의 사용자 ID",
            example = "f47ac10b-58cc-4372-a567-0e02b2c3d479"
    )
    private UUID userId;

    @Schema(
            description = "체크인한 학생의 이름",
            example = "김철수"
    )
    private String userName;

    @Schema(
            description = "해당 출석 세션의 ID",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    private UUID attendanceSessionId;

    @Schema(
            description = "해당 출석 라운드의 ID",
            example = "b5c3d4e5-f6a7-8901-bcde-f12345678901"
    )
    private UUID attendanceRoundId;

    @Schema(
            description = "출석 상태. PRESENT(출석), LATE(지각), ABSENT(결석), EXCUSED(사유결석)",
            example = "PRESENT",
            implementation = AttendanceStatus.class
    )
    private AttendanceStatus attendanceStatus;

    @Schema(
            description = "실제 체크인 시간 (ISO 8601 형식)",
            example = "2024-10-31 14:30:15"
    )
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkedAt;

    @Schema(
            description = "체크인으로 인해 획득한 포인트",
            example = "10"
    )
    private Integer awardedPoints;

    @Schema(
            description = "체크인 시 작성한 메모",
            example = "교실 입구에서 체크인했습니다"
    )
    private String note;

    @Schema(
            description = "체크인 시의 위도",
            example = "37.4979"
    )
    private Double checkInLatitude;

    @Schema(
            description = "체크인 시의 경도",
            example = "127.0276"
    )
    private Double checkInLongitude;

    @Schema(
            description = "체크인에 사용한 디바이스 정보",
            example = "iPhone 14 Pro"
    )
    private String deviceInfo;

    @Schema(
            description = "지각 여부. true면 지각(5분 이상 경과 후 체크인)",
            example = "false"
    )
    private boolean isLate;

    @Schema(
            description = "출석 기록 생성 시간",
            example = "2024-10-31 14:30:15"
    )
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(
            description = "출석 기록 최종 수정 시간",
            example = "2024-10-31 15:00:00"
    )
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
