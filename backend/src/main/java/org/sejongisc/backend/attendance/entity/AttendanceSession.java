package org.sejongisc.backend.attendance.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.time.LocalDateTime;
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

    private String tag;         // "금융IT 출석", "동아리 전체"

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;     // 세션 시작 시간

    @Column(name = "window_seconds")
    private Integer windowSeconds;      // 체크인 가능 시간(초) - 1800 = 30분

    @Column(unique = true, length = 6)
    private String code;            // 6자리 출석 코드 "942715"

    @Column(name = "reward_points")
    private Integer rewardPoints;       // 출석 시 지급할 포인트

    @Embedded
    private Location location;      // 위치 기반 출석을 위한 GPS 좌표

    @Enumerated(EnumType.STRING)
    private SessionVisibility visibility;

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
     * 현재 세션 상태 계산
     */
    public SessionStatus calculateCurrentStatus() {
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(startsAt)) {
            return SessionStatus.UPCOMING;
        } else if (now.isAfter(getEndsAt())) {
            return SessionStatus.CLOSED;
        } else {
            return SessionStatus.OPEN;
        }
    }

    /**
     * 세션 종료 시간 계산
     */
    public boolean isCheckInAvailable() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startsAt) && now.isBefore(getEndsAt());
    }

    /**
     * 세션 종료 시간 계산
     */
    public LocalDateTime getEndsAt() {
        return startsAt.plusSeconds(windowSeconds != null ? windowSeconds : 1800);
    }

    /**
     * 남은 시간 계산 (초단위)
     */
    public long getRemainingSeconds() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endsAt = getEndsAt();

        if (now.isAfter(endsAt)) {
            return 0;
        }

        return java.time.Duration.between(now, endsAt).getSeconds();
    }
}
