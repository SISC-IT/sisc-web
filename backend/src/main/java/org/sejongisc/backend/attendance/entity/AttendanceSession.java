package org.sejongisc.backend.attendance.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

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
    private String title;       // "금융 IT팀 세션"

    @Column(name = "default_start_time", nullable = false)
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime defaultStartTime;     // 기본 시작 시간 (시간만) - 18:30:00

    @Column(name = "default_available_minutes")
    private Integer defaultAvailableMinutes;      // 출석 인정 시간(분) - 30분

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

    @OneToMany(mappedBy = "attendanceSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    @Builder.Default
    private List<Attendance> attendances = new ArrayList<>();

    /**
     * 세션 상태를 반환합니다.
     * - UPCOMING: 활성화되지 않은 상태
     * - OPEN: 관리자가 활성화한 상태 (체크인 가능)
     * - CLOSED: 관리자가 종료한 상태 (체크인 불가)
     */
    public SessionStatus getCurrentStatus() {
        return this.status;
    }

    /**
     * 체크인이 가능한 상태인지 확인합니다.
     */
    public boolean isCheckInAvailable() {
        return SessionStatus.OPEN.equals(this.status);
    }
}
