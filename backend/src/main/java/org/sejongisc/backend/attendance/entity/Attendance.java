package org.sejongisc.backend.attendance.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attendance extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attendance_id", columnDefinition = "uuid")
    private UUID attendanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonBackReference
    private AttendanceSession attendanceSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = true)
    @JsonBackReference
    private AttendanceRound attendanceRound;

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

    @Column(name = "anonymous_user_name", length = 100)
    private String anonymousUserName;

    // 지각 여부 계산 / 상태 업데이트

    /**
     * 지각 여부 판단
     */
    public boolean isLate() {
        if (checkedAt == null || attendanceSession.getStartsAt() == null) {
            return false;
        }
        return checkedAt.isAfter(attendanceSession.getStartsAt());
    }

    /**
     * 상태 업데이트 (관리자용)
     */
    public void updateStatus(AttendanceStatus newStatus, String reason) {
        this.attendanceStatus = newStatus;
        if (reason != null && !reason.trim().isEmpty()) {
            this.note = reason;
        }
    }

    /**
     * 출석 시간 자동 설정
     */
    public void markPresent() {
        this.attendanceStatus = AttendanceStatus.PRESENT;
        this.checkedAt = LocalDateTime.now();
    }

    /**
     * 지각 처리
     */
    public void markLate() {
        this.attendanceStatus = AttendanceStatus.LATE;
        this.checkedAt = LocalDateTime.now();
    }
}

