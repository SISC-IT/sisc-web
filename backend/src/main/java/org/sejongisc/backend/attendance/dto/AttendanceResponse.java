package org.sejongisc.backend.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.sejongisc.backend.attendance.entity.AttendanceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponse {

    private UUID attendanceId;
    private UUID userId;
    private String userName;
    private UUID attendanceSessionId;
    private AttendanceStatus attendanceStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkedAt;

    private Integer awardedPoints;
    private String note;
    private Double checkInLatitude;
    private Double checkInLongitude;
    private String deviceInfo;
    private boolean isLate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
