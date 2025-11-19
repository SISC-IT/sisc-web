package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.entity.SessionVisibility;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        title = "출석 세션 응답",
        description = "출석 세션의 상세 정보. 세션 설정, 상태, 남은 시간, 참여자 수 등을 포함합니다."
)
public class AttendanceSessionResponse {

    @Schema(
            description = "출석 세션의 고유 ID",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    private UUID attendanceSessionId;

    @Schema(
            description = "세션의 제목/이름",
            example = "2024년 10월 동아리 정기 모임"
    )
    private String title;

    @Schema(
            description = "세션의 분류 태그",
            example = "금융IT"
    )
    private String tag;

    @Schema(
            description = "세션 시작 시간",
            example = "2024-11-15 14:00:00"
    )
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startsAt;

    @Schema(
            description = "출석 체크인이 가능한 시간 윈도우 (초 단위)",
            example = "1800"
    )
    private Integer windowSeconds;

    @Schema(
            description = "출석 세션의 6자리 코드. 학생들이 체크인 시 입력합니다.",
            example = "ABC123"
    )
    private String code;

    @Schema(
            description = "출석 완료 시 지급할 포인트",
            example = "10"
    )
    private Integer rewardPoints;

    @Schema(
            description = "세션 개최 위치의 위도",
            example = "37.4979"
    )
    private Double latitude;

    @Schema(
            description = "세션 개최 위치의 경도",
            example = "127.0276"
    )
    private Double longitude;

    @Schema(
            description = "GPS 기반 위치 검증 반경 (미터 단위)",
            example = "100"
    )
    private Integer radiusMeters;

    @Schema(
            description = "세션의 공개 범위. PUBLIC(공개) 또는 PRIVATE(비공개)",
            example = "PUBLIC",
            implementation = SessionVisibility.class
    )
    private SessionVisibility visibility;

    @Schema(
            description = "세션의 현재 상태. UPCOMING(예정), OPEN(진행중), CLOSED(종료)",
            example = "OPEN",
            implementation = SessionStatus.class
    )
    private SessionStatus status;

    @Schema(
            description = "세션 레코드 생성 시간",
            example = "2024-10-31 10:00:00"
    )
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(
            description = "세션 레코드 최종 수정 시간",
            example = "2024-10-31 11:30:00"
    )
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @Schema(
            description = "세션의 예상 종료 시간 (시작시간 + 윈도우)",
            example = "2024-11-15 14:30:00"
    )
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endsAt;

    @Schema(
            description = "현재부터 체크인 마감까지 남은 시간 (초 단위). 음수이면 마감됨",
            example = "1234"
    )
    private Long remainingSeconds;

    @Schema(
            description = "현재 체크인이 가능한 상태인지 여부. " +
                    "true면 지금 체크인 가능, false면 불가능",
            example = "true"
    )
    private boolean checkInAvailable;

    @Schema(
            description = "현재 세션에 참여한 학생 수",
            example = "25"
    )
    private Integer participantCount;
}