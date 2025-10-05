package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.entity.SessionVisibility;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSessionResponse {

    private UUID attendanceSessionId;
    private String title;
    private String tag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startsAt;

    private Integer windowSeconds;
    private String code;
    private Integer rewardPoints;
    private Double latitude;
    private Double longitude;
    private Integer radiusMeters;
    private SessionVisibility visibility;
    private SessionStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endsAt;

    private Long remainingSeconds;
    private boolean checkInAvailable;
    private Integer participantCount;
}
