package org.sejongisc.backend.attendance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long attendanceId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    @CreationTimestamp
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(name = "awarded_points")
    private Integer awardedPoints;

    @Column(length = 500)
    private String note;

    @Embedded
    private Location checkInLocation;

    // 지각 여부 계산 / 상태 업데이트
}

