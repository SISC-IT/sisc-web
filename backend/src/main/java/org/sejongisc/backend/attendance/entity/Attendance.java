package org.sejongisc.backend.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.user.entity.User;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "attendance",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_attendance_round_user",
        columnNames = {"round_id", "user_id"}
    )
)
public class Attendance extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attendance_id", columnDefinition = "uuid")
    private UUID attendanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private AttendanceRound attendanceRound;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus attendanceStatus;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @CreationTimestamp
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    // todo 지각 사유나 특이사항 적는칸-> 개인이 작성하면 관리자만 볼 수 있게 해야할거 같은디
    @Column(length = 500)
    private String note;

    @Embedded
    private Location checkInLocation;

    // 지각 여부 계산 / 상태 업데이트
    public void changeStatus(AttendanceStatus newStatus, String reason) {
        if (newStatus == null) return;
        this.attendanceStatus = newStatus;

        if (reason != null && !reason.isBlank()) {
            this.note = reason;
        }
    }

    public void recordLocation(Location location) {
        this.checkInLocation = location;
    }
}
