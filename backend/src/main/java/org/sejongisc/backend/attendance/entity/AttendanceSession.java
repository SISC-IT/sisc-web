package org.sejongisc.backend.attendance.entity;

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
    private String title;       // "세투연 9/17"

    private String description;

    @Column(name = "allowed_minutes", nullable = false)
    private Integer allowedMinutes;         // 출석 인정 시간(분) - 예: 30분

    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    // 라운드 목록 조회용
    @OneToMany(mappedBy = "attendanceSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<AttendanceRound> rounds = new ArrayList<>();

    @OneToMany(mappedBy = "attendanceSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<SessionUser> sessionUsers = new ArrayList<>();



}
