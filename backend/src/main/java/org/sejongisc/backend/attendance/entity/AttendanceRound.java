package org.sejongisc.backend.attendance.entity;

import static java.time.Duration.ofMinutes;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 출석 세션 내 개별 라운드(주차)
 */
@Entity
@Getter
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
    private AttendanceSession attendanceSession;

    @Column(nullable = false)
    private LocalDate roundDate;              // 라운드 날짜 (예: 2025-11-06)

    @Column(nullable = false)
    private LocalDateTime startAt;              // 시작 시간 미리 예약


    private LocalDateTime closeAt;                // 종료 시간 관리자가 설정 or 일정시간 경과시 자동 설정

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    // todo 라운드 상태 관리 로직 필요
    // 생성시 upcoming, 출석 시작시 active, 출석 종료시 closed
    private RoundStatus roundStatus;          // UPCOMING, ACTIVE, CLOSED


    @Column(name = "round_name", length = 255, nullable = false)
    private String roundName;                 // 라운드 이름 (예: "1차 정기모임", "OT" 등)

    private String locationName;              // 장소 이름 (예: "세종대학교 310동")

    // todo 라운드별 관리자에게만 발급되는 큐알 코드는 필요할 거 같음
    @Column(name = "qr_secret", nullable = false, length = 120)
    private String qrSecret;

    // 라운드별 참석 조회용
    @OneToMany(mappedBy = "attendanceRound", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<Attendance> attendances = new ArrayList<>();

    /**
     * 상태 변경
     */

    public void changeStatus(RoundStatus newStatus) {
        // 종료된 라운드는 상태 변경 불가
        if (this.roundStatus == RoundStatus.CLOSED) {
            return;
        }

        if(this.roundStatus == RoundStatus.ACTIVE &&newStatus == RoundStatus.UPCOMING) {
            // ACTIVE -> UPCOMING 불가
            return;
        }


        if (this.roundStatus == newStatus) {
            return; // 이미 그 상태이면 무시 (DB 쿼리 방지)
        }
        this.roundStatus = newStatus;
    }




}
