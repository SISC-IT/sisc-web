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
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonBackReference
    private AttendanceSession attendanceSession;

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

    /**
         * Determines whether this attendance record is late.
         *
         * If either `checkedAt` or the attendance session's start time is null, this method returns `false`.
         *
         * @return `true` if `checkedAt` is after the session's start time, `false` otherwise.
         */
    public boolean isLate() {
        if (checkedAt == null || attendanceSession.getStartsAt() == null) {
            return false;
        }
        return checkedAt.isAfter(attendanceSession.getStartsAt());
    }

    /**
     * Update the attendance status and optionally record a reason.
     *
     * @param newStatus the new AttendanceStatus to set
     * @param reason    an optional reason to store in the attendance note; if null or empty after trimming it is ignored
     */
    public void updateStatus(AttendanceStatus newStatus, String reason) {
        this.attendanceStatus = newStatus;
        if (reason != null && !reason.trim().isEmpty()) {
            this.note = reason;
        }
    }

    /**
     * Mark this attendance as present and record the current time.
     *
     * Sets the attendanceStatus to PRESENT and updates checkedAt to the current system time.
     */
    public void markPresent() {
        this.attendanceStatus = AttendanceStatus.PRESENT;
        this.checkedAt = LocalDateTime.now();
    }

    /**
     * Marks this attendance as late and records the current check-in time.
     *
     * Sets the attendance status to `LATE` and updates `checkedAt` to the current system time.
     */
    public void markLate() {
        this.attendanceStatus = AttendanceStatus.LATE;
        this.checkedAt = LocalDateTime.now();
    }
}
