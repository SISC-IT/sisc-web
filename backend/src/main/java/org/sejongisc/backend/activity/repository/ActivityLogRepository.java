package org.sejongisc.backend.activity.repository;

import org.sejongisc.backend.activity.entity.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // 이슈 1: 메인 대시보드 실시간 로그 (최신순 20개)
    List<ActivityLog> findTop20ByOrderByCreatedAtDesc();

    // 이슈 2: 마이페이지 내 활동 조회
    Slice<ActivityLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // 이슈 3-1: 일일 방문자 수 통계
    @Query("SELECT COUNT(DISTINCT a.userId) FROM ActivityLog a " +
           "WHERE a.type = 'AUTH_LOGIN' AND a.createdAt BETWEEN :start AND :end")
    long countDailyUniqueVisitors(LocalDateTime start, LocalDateTime end);

    // 이슈 3-2: 게시판별 활동량 집계 (게시글+댓글+좋아요)
    @Query("SELECT a.boardName, COUNT(a) FROM ActivityLog a " +
           "WHERE a.type IN ('BOARD_POST', 'BOARD_COMMENT', 'BOARD_LIKE') " +
           "AND a.createdAt BETWEEN :start AND :end " +
           "GROUP BY a.boardName")
    List<Object[]> countActivityByBoard(LocalDateTime start, LocalDateTime end);
}