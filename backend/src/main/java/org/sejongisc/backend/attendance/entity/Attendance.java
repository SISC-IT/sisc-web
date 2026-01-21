package org.sejongisc.backend.attendance.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalTime;
import lombok.*;
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

