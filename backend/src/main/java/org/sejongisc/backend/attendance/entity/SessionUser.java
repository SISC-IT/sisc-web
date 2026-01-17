package org.sejongisc.backend.attendance.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.user.entity.User;

import java.util.UUID;

/**
 * 세션에 참여하는 사용자를 관리하는 엔티티
 *
 */
@Entity
@Table(
        name = "attendance_session_user",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"session_id", "user_id"},
                name = "uk_session_user"
        ),
        indexes = {
                @Index(name = "idx_session_id", columnList = "session_id"),
                @Index(name = "idx_user_id", columnList = "user_id")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionUser extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_user_id", columnDefinition = "uuid")
    private UUID sessionUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession attendanceSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 세션 내 사용자 역할
    @Enumerated(EnumType.STRING)
    private SessionRole sessionRole;


    /**
     * toString 오버라이드 (순환 참조 방지)
     */
    @Override
    public String toString() {
        return "SessionUser{" +
                "sessionUserId=" + sessionUserId +
                ", sessionId=" + (attendanceSession != null ? attendanceSession.getAttendanceSessionId() : null) +
                ", userId=" + (user != null ? user.getUserId() : null) +
                ", createdDate=" + getCreatedDate() +
                '}';
    }
}
