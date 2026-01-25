package org.sejongisc.backend.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

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
}
