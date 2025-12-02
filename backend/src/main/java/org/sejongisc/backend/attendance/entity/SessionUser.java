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
 * 예: "금융동아리 2024년 정기 모임" 세션에 참여하는 팀원들
 * - 참여자 추가/삭제 관리
 * - 세션별 참여자 조회
 * - 중간 참여시 이전 라운드는 자동으로 결석 처리
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
@Setter
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
    @JsonBackReference
    private User user;

    @Column(name = "user_name", length = 100, nullable = false)
    private String userName;  // 저장 시점의 user.name 캐시 (나중에 user.name이 변경되어도 유지)

    /**
     * toString 오버라이드 (순환 참조 방지)
     */
    @Override
    public String toString() {
        return "SessionUser{" +
                "sessionUserId=" + sessionUserId +
                ", sessionId=" + (attendanceSession != null ? attendanceSession.getAttendanceSessionId() : null) +
                ", userId=" + (user != null ? user.getUserId() : null) +
                ", userName='" + userName + '\'' +
                ", createdDate=" + getCreatedDate() +
                '}';
    }
}
