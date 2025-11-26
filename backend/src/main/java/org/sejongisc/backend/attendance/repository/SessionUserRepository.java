package org.sejongisc.backend.attendance.repository;

import org.sejongisc.backend.attendance.entity.SessionUser;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionUserRepository extends JpaRepository<SessionUser, UUID> {

    /**
     * 특정 세션의 모든 참여자 조회
     */
    @Query("SELECT su FROM SessionUser su WHERE su.attendanceSession.attendanceSessionId = :sessionId ORDER BY su.createdDate ASC")
    List<SessionUser> findBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * 특정 사용자가 참여하는 모든 세션 조회
     */
    @Query("SELECT su FROM SessionUser su WHERE su.user.userId = :userId ORDER BY su.createdDate DESC")
    List<SessionUser> findByUserId(@Param("userId") UUID userId);

    /**
     * 세션과 사용자 조합으로 조회
     */
    @Query("SELECT su FROM SessionUser su WHERE su.attendanceSession.attendanceSessionId = :sessionId AND su.user.userId = :userId")
    Optional<SessionUser> findBySessionIdAndUserId(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId);

    /**
     * 세션에 특정 사용자가 참여하는지 확인
     */
    @Query("SELECT COUNT(su) > 0 FROM SessionUser su WHERE su.attendanceSession.attendanceSessionId = :sessionId AND su.user.userId = :userId")
    boolean existsBySessionIdAndUserId(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId);

    /**
     * 세션의 참여자 수
     */
    @Query("SELECT COUNT(su) FROM SessionUser su WHERE su.attendanceSession.attendanceSessionId = :sessionId")
    long countBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * 세션의 모든 참여자 삭제
     */
    @Query("DELETE FROM SessionUser su WHERE su.attendanceSession.attendanceSessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * 세션에서 특정 사용자 삭제
     */
    @Query("DELETE FROM SessionUser su WHERE su.attendanceSession.attendanceSessionId = :sessionId AND su.user.userId = :userId")
    void deleteBySessionIdAndUserId(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId);
}
