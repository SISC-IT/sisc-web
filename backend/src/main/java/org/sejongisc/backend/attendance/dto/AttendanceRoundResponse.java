package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.sejongisc.backend.attendance.entity.RoundStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRoundResponse {

    private UUID roundId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate roundDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private Integer allowedMinutes;

    private RoundStatus roundStatus;

    private String roundName;

    private Long presentCount;

    private Long lateCount;

    private Long absentCount;

    private Long totalAttendees;

    /**
     * 엔티티를 DTO로 변환
     * roundStatus는 실시간으로 계산되어 반환됨
     */
    public static AttendanceRoundResponse fromEntity(AttendanceRound round) {
        long presentCount = round.getAttendances().stream()
                .filter(a -> a.getAttendanceStatus().toString().equals("PRESENT"))
                .count();
        long lateCount = round.getAttendances().stream()
                .filter(a -> a.getAttendanceStatus().toString().equals("LATE"))
                .count();
        long absentCount = round.getAttendances().stream()
                .filter(a -> a.getAttendanceStatus().toString().equals("ABSENT"))
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
