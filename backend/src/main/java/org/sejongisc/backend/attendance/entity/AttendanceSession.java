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

    @OneToMany(mappedBy = "attendanceSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    @Builder.Default
    private List<Attendance> attendances = new ArrayList<>();

    /**
     * Determine the session's current status based on the current time relative to its start and end.
     *
     * @return `UPCOMING` if the current time is before `startsAt`, `CLOSED` if it is after `getEndsAt()`, `OPEN` otherwise.
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
     * Determines whether check-in is currently permitted for the session.
     *
     * Uses the system clock to compare the current time against the session start and end (start plus the configured window).
     *
     * @return `true` if the current time is strictly after the session start and strictly before the session end, `false` otherwise.
     */
    public boolean isCheckInAvailable() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startsAt) && now.isBefore(getEndsAt());
    }

    /**
     * Compute the session end time based on the start time and configured attendance window.
     *
     * If `windowSeconds` is null, a default of 1800 seconds (30 minutes) is used.
     *
     * @return the end time equal to `startsAt` plus the attendance window in seconds
     */
    public LocalDateTime getEndsAt() {
        return startsAt.plusSeconds(windowSeconds != null ? windowSeconds : 1800);
    }

    /**
     * Compute the number of seconds remaining until the session ends.
     *
     * @return Number of seconds remaining until the session end; `0` if the session has already ended.
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