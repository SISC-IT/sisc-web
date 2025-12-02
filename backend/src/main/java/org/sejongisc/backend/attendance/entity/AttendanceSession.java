package org.sejongisc.backend.attendance.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSession extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attendance_session_id", columnDefinition = "uuid")
    private UUID attendanceSessionId;

    @Column(nullable = false)
    private String title;       // "세투연 9/17"

    @Column(name = "default_start_time", nullable = false)
    private LocalTime defaultStartTime;     // 세션 기본 시작 시간 (예: 18:30:00)

    @Column(name = "allowed_minutes", nullable = false)
    private Integer allowedMinutes;         // 출석 인정 시간(분) - 예: 30분

    @Column(unique = true, length = 6)
    private String code;            // 6자리 출석 코드 "942715"

    @Column(name = "reward_points")
    private Integer rewardPoints;       // 출석 시 지급할 포인트

    @Embedded
    private Location location;      // 위치 기반 출석을 위한 GPS 좌표

    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    @OneToMany(mappedBy = "attendanceSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<AttendanceRound> rounds = new ArrayList<>();

    @OneToMany(mappedBy = "attendanceSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<SessionUser> sessionUsers = new ArrayList<>();

    @OneToMany(mappedBy = "attendanceSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<Attendance> attendances = new ArrayList<>();

    /**
     * 세션 종료 시간 계산 (시간만)
     */
    public LocalTime getEndTime() {
        return defaultStartTime.plusMinutes(allowedMinutes != null ? allowedMinutes : 30);
    }

    /**
     * 특정 라운드 날짜에서 세션이 진행 중인지 확인
     */
    public boolean isCheckInAvailableForRound(java.time.LocalTime currentTime) {
        return !currentTime.isBefore(defaultStartTime) && currentTime.isBefore(getEndTime());
    }

    /**
     * 현재 세션 상태 계산 (라운드별)
     */
    public SessionStatus calculateCurrentStatus() {
        return SessionStatus.OPEN;
    }
}
