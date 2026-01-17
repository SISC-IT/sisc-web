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
    @lombok.Setter
    private AttendanceStatus attendanceStatus;

    @CreationTimestamp
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    // todo User의 point와 동기화 필요
    // 출석했을때 무조건 100포인트라면 굳이 필요 없을 듯
    // 이거 session에 정해져 있지 않나 여기에 없어도 될거 같은데
    @Column(name = "awarded_points")
    @lombok.Setter
    private Integer awardedPoints;

    // todo 지각 사유나 특이사항 적는칸-> 개인이 작성하면 관리자만 볼 수 있게 해야할거 같은디
    @Column(length = 500)
    @lombok.Setter
    private String note;

    @Embedded
    @lombok.Setter
    private Location checkInLocation;


    // 지각 여부 계산 / 상태 업데이트



}

