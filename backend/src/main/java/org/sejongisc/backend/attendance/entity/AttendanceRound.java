package org.sejongisc.backend.attendance.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 출석 세션 내 개별 라운드(주차)
 *
 * 예: "금융동아리 2024년 정기 모임" 세션 내
 *     - 라운드 1: 2025-11-06, 10:00~11:00
 *     - 라운드 2: 2025-11-13, 10:00~11:00
 */
@Entity
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRound extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "round_id", columnDefinition = "uuid")
    private UUID roundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonBackReference
    private AttendanceSession attendanceSession;

    @Column(nullable = false)
    private LocalDate roundDate;              // 라운드 날짜 (예: 2025-11-06)

    @Column(nullable = false)
    private LocalTime startTime;              // 출석 시작 시간 (예: 10:00)

    @Column(nullable = false)
    private Integer allowedMinutes;           // 출석 인정 시간 (분단위, 예: 30)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoundStatus roundStatus;          // UPCOMING, ACTIVE, CLOSED

    @Column(name = "round_name", length = 255, nullable = true)
    private String roundName;                 // 라운드 이름 (예: "1차 정기모임", "OT" 등)

    @OneToMany(mappedBy = "attendanceRound", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<Attendance> attendances = new ArrayList<>();

    /**
     * 현재 라운드 상태 계산
     * - UPCOMING: 라운드 날짜 이전 또는 당일이지만 시작시간 이전
     * - ACTIVE: 시작시간부터 종료시간 사이
     * - CLOSED: 라운드 날짜 이후 또는 당일이지만 종료시간 이후
     */
    public RoundStatus calculateCurrentStatus() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (today.isBefore(roundDate)) {
            return RoundStatus.UPCOMING;
        }

        if (today.isAfter(roundDate)) {
            return RoundStatus.CLOSED;
        }

        // today.equals(roundDate)인 경우
        if (now.isBefore(startTime)) {
            return RoundStatus.UPCOMING;
        }

        if (now.isAfter(getEndTime())) {
            return RoundStatus.CLOSED;
        }

        // startTime <= now <= endTime
        return RoundStatus.ACTIVE;
    }

    /**
     * 출석 종료 시간 계산
     */
    public LocalTime getEndTime() {
        return startTime.plusMinutes(allowedMinutes != null ? allowedMinutes : 30);
    }

    /**
     * 해당 라운드에서 출석 가능 여부 확인
     */
    public boolean isCheckInAvailable() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (!today.equals(roundDate)) {
            return false;
        }

        return !now.isBefore(startTime) && now.isBefore(getEndTime());
    }

    /**
     * 라운드 정보 업데이트
     */
    public void updateRoundInfo(LocalDate newDate, LocalTime newStartTime, Integer newAllowedMinutes) {
        this.roundDate = newDate;
        this.startTime = newStartTime;
        this.allowedMinutes = newAllowedMinutes;
        this.roundStatus = calculateCurrentStatus();
    }
}
