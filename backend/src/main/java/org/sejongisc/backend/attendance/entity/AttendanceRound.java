package org.sejongisc.backend.attendance.entity;

import static java.time.Duration.ofMinutes;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.Duration;
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
    private LocalTime startTime;              // 시작 시간 (예: 10:00)

    @Column(nullable = false)
    private LocalTime endTime;                // 종료 시간 (예: 10:20)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)

    // todo 라운드 상태 관리 로직 필요
    // 생성시 upcoming, 출석 시작시 active, 출석 종료시 closed
    private RoundStatus roundStatus;          // UPCOMING, ACTIVE, CLOSED


    @Column(name = "round_name", length = 255, nullable = true)
    private String roundName;                 // 라운드 이름 (예: "1차 정기모임", "OT" 등)

    @Column(nullable = false)
    private String locationName;              // 장소 이름 (예: "세종대학교 310동")

    // todo 라운드별 관리자에게만 발급되는 큐알 코드는 필요할 거 같음
    private String qrCode;                    // 라운드별 출석 QR 코드 문자열

    // 라운드별 참석 조회용
    @OneToMany(mappedBy = "attendanceRound", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<Attendance> attendances = new ArrayList<>();





}
