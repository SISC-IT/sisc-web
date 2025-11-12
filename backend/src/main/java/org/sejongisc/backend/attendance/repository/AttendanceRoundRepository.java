package org.sejongisc.backend.attendance.repository;

import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRoundRepository extends JpaRepository<AttendanceRound, UUID> {

    /**
     * 세션 ID로 해당 세션의 모든 라운드 조회
     */
    List<AttendanceRound> findByAttendanceSession_AttendanceSessionIdOrderByRoundDateAsc(UUID sessionId);

    /**
     * 세션 ID와 라운드 날짜로 조회
     */
    Optional<AttendanceRound> findByAttendanceSession_AttendanceSessionIdAndRoundDate(UUID sessionId, LocalDate roundDate);

    /**
     * 특정 라운드 ID로 조회 (출석 시 필요)
     */
    @Query("SELECT r FROM AttendanceRound r " +
            "WHERE r.roundId = :roundId")
    Optional<AttendanceRound> findRoundById(@Param("roundId") UUID roundId);

    /**
     * 특정 세션의 라운드 개수
     */
    long countByAttendanceSession_AttendanceSessionId(UUID sessionId);

    /**
     * 특정 세션 내 특정 라운드 번호의 라운드 조회
     */
    @Query("SELECT r FROM AttendanceRound r " +
            "WHERE r.attendanceSession.attendanceSessionId = :sessionId " +
            "ORDER BY r.roundDate ASC " +
            "LIMIT 1 OFFSET :offset")
    Optional<AttendanceRound> findNthRoundInSession(@Param("sessionId") UUID sessionId, @Param("offset") int offset);
}
