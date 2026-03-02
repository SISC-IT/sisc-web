package org.sejongisc.backend.activity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "activity_log", indexes = {
    @Index(name = "idx_activity_user_id", columnList = "userId"),
    @Index(name = "idx_activity_created_at", columnList = "createdAt")
})
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String username; // 조회 시 조인 부하를 줄이기 위해 이름 스냅샷 저장

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activityType; // ATTENDANCE, BOARD, BETTING 등

    @Column(nullable = false, length = 30)
    private String message; // "자유게시판에 글을 게시했어요"

    private UUID targetId; // 관련 게시글 ID 등 (상세보기용)
    
    private String boardName; // 관리자 게시판별 통계용

    private LocalDateTime createdAt;

    @Builder
    public ActivityLog(UUID userId, String username, ActivityType activityType, String message, UUID targetId, String boardName) {
        this.userId = userId;
        this.username = username;
        this.activityType = activityType;
        this.message = message;
        this.targetId = targetId;
        this.boardName = boardName;
        this.createdAt = LocalDateTime.now();
    }
}