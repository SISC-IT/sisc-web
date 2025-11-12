package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.sejongisc.backend.attendance.entity.RoundStatus;
import org.sejongisc.backend.attendance.entity.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        title = "출석 라운드 응답",
        description = "출석 라운드의 상세 정보. 라운드 상태, 시간, 출석 현황 통계를 포함합니다."
)
public class AttendanceRoundResponse {

    @Schema(
            description = "라운드의 고유 ID",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    private UUID roundId;

    @Schema(
            description = "라운드 진행 날짜",
            example = "2025-11-06",
            type = "string",
            format = "date"
    )
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate roundDate;

    @Schema(
            description = "라운드 출석 시작 시간",
            example = "10:00",
            type = "string",
            format = "time"
    )
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @Schema(
            description = "라운드 출석 종료 시간 (startTime + allowedMinutes)",
            example = "10:30",
            type = "string",
            format = "time"
    )
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    @Schema(
            description = "출석 가능한 시간 (분단위)",
            example = "30"
    )
    private Integer allowedMinutes;

    @Schema(
            description = "라운드의 현재 상태. UPCOMING(시작 전), ACTIVE(진행 중), CLOSED(종료됨)",
            example = "ACTIVE",
            implementation = RoundStatus.class
    )
    private RoundStatus roundStatus;

    @Schema(
            description = "라운드의 이름/제목. 예: 1주차, 2주차 등",
            example = "1주차"
    )
    private String roundName;

    @Schema(
            description = "정시 출석자 수",
            example = "20"
    )
    private Long presentCount;

    @Schema(
            description = "지각 출석자 수",
            example = "5"
    )
    private Long lateCount;

    @Schema(
            description = "결석자 수",
            example = "3"
    )
    private Long absentCount;

    @Schema(
            description = "총 출석자 수",
            example = "28"
    )
    private Long totalAttendees;

    /**
     * 엔티티를 DTO로 변환
     * roundStatus는 실시간으로 계산되어 반환됨
     */
    public static AttendanceRoundResponse fromEntity(AttendanceRound round) {
        long presentCount = round.getAttendances().stream()
                .filter(a -> a.getAttendanceStatus() == AttendanceStatus.PRESENT)
                .count();
        long lateCount = round.getAttendances().stream()
                .filter(a -> a.getAttendanceStatus() == AttendanceStatus.LATE)
                .count();
        long absentCount = round.getAttendances().stream()
                .filter(a -> a.getAttendanceStatus() == AttendanceStatus.ABSENT)
                .count();

        // 현재 시간 기준으로 라운드 상태를 실시간 계산
        RoundStatus currentStatus = round.calculateCurrentStatus();

        return AttendanceRoundResponse.builder()
                .roundId(round.getRoundId())
                .roundDate(round.getRoundDate())
                .startTime(round.getStartTime())
                .endTime(round.getEndTime())
                .allowedMinutes(round.getAllowedMinutes())
                .roundStatus(currentStatus)
                .roundName(round.getRoundName())
                .presentCount(presentCount)
                .lateCount(lateCount)
                .absentCount(absentCount)
                .totalAttendees((long) round.getAttendances().size())
                .build();
    }
}
