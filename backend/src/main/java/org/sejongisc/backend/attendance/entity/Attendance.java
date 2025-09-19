package org.sejongisc.backend.attendance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "attendance_id", columnDefinition = "uuid")
    private UUID attendanceId;

    @Column(name = "session_id", nullable = false, columnDefinition = "uuid")
    private UUID attendanceSessionId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus attendanceStatus;

    @CreationTimestamp
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(name = "awarded_points")
    private Integer awardedPoints;

    @Column(length = 500)
    private String note;

    @Embedded
    private Location checkInLocation;

    @Column(name = "device_info")
    private String deviceInfo;

    // 지각 여부 계산 / 상태 업데이트
}

