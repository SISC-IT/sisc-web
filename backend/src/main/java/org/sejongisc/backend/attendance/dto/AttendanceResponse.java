package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponse {

    // === 기본 응답 정보 ===
    private String message;
    private boolean success;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime responseTime;

    // === 출석 기록 정보 ===
    private UUID attendanceId;
    private UUID userId;
    private String userName;
    private String status; // PRESENT, LATE, ABSENT, EXCUSED

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkedAt;
    private Integer awardPoints;

    // === 세션 정보 ===
    private UUID sessionId;
    private String sessionTitle;
    private String sessionTag;
    private String sessionCode;
    private String sessionStatus; // UPCOMING, OPEN, CLOSED

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sessionStartsAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sessionEndsAt;

    private Long remainingSeconds; // UI의 타이머용

    // === 위치 정보 ===
    private Double latitude;
    private Double longitude;
    private Integer radiusMeters;

    // === 출석자 목록 (관리자용) ===
    private List<AttendanceInfo> attendance;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendanceInfo{
        private UUID userId;
        private String userName;
        private String status;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime checkedAt;
        private String note;
    }
}
