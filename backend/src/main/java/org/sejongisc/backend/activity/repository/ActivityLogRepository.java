package org.sejongisc.backend.activity.repository;

import org.sejongisc.backend.activity.entity.ActivityLog;
import org.sejongisc.backend.activity.entity.ActivityType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    // 마이페이지 내 활동 조회
    @Query("SELECT a FROM ActivityLog a WHERE a.userId = :userId " +
            "AND a.activityType IN :activityTypes " +
            "ORDER BY a.createdAt DESC")
    List<ActivityLog> findByUserIdAndActivityTypesOrderByCreatedAtDesc(UUID userId, List<ActivityType> activityTypes);

    @Query("SELECT COUNT(a) FROM ActivityLog a " +
        "WHERE a.activityType IN :types " +
        "AND a.createdAt BETWEEN :start AND :end")
    long countActivitiesByTypeAndPeriod(@Param("types") List<ActivityType> types, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 일일 방문자 수 통계
    @Query("SELECT COUNT(DISTINCT a.userId) FROM ActivityLog a " +
           "WHERE a.activityType = 'AUTH_LOGIN' AND a.createdAt BETWEEN :start AND :end")
    long countDailyUniqueVisitors(LocalDateTime start, LocalDateTime end);

    // 날짜별 방문자 추이
    @Query(value = "SELECT DATE(created_at) as date, COUNT(DISTINCT user_id) as count " +
        "FROM activity_log " +
        "WHERE activity_type = 'AUTH_LOGIN' AND created_at >= :startDate " +
        "GROUP BY DATE(created_at) ORDER BY date ASC", nativeQuery = true)
    List<Object[]> getDailyVisitorTrendNative(@Param("startDate") LocalDateTime startDate);

    // 게시판별 활동량 집계 (게시글+댓글+좋아요)
    @Query("SELECT a.boardName, COUNT(a) FROM ActivityLog a " +
        "WHERE a.activityType IN ('BOARD_POST', 'BOARD_COMMENT', 'BOARD_LIKE') " +
        "AND (:start IS NULL OR a.createdAt >= :start) " +
        "AND (:end IS NULL OR a.createdAt <= :end) " +
        "GROUP BY a.boardName")
    List<Object[]> countActivityByBoard(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Slice<ActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}