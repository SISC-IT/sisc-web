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
     * nativeQuery=true를 사용하여 SQL의 LIMIT/OFFSET을 지원
     */
    @Query(value = "SELECT * FROM attendance_round " +
            "WHERE attendance_session_id = :sessionId " +
            "ORDER BY round_date ASC " +
            "LIMIT 1 OFFSET :offset", nativeQuery = true)
    Optional<AttendanceRound> findNthRoundInSession(@Param("sessionId") UUID sessionId, @Param("offset") int offset);

    /**
     * 세션의 특정 날짜 이전의 모든 라운드 조회
     * - 세션에 유저 추가 시, 이전 라운드들에 자동으로 결석 처리하기 위해 사용
     */
    @Query("SELECT r FROM AttendanceRound r " +
            "WHERE r.attendanceSession.attendanceSessionId = :sessionId " +
            "AND r.roundDate < :date " +
            "ORDER BY r.roundDate ASC")
    List<AttendanceRound> findBySession_SessionIdAndRoundDateBefore(
            @Param("sessionId") UUID sessionId,
            @Param("date") LocalDate date);

    /**
     * 세션의 특정 날짜 이후의 모든 라운드 조회
     * - 세션에 유저 추가 시, 미래 라운드들에 자동으로 PENDING 처리하기 위해 사용
     */
    @Query("SELECT r FROM AttendanceRound r " +
            "WHERE r.attendanceSession.attendanceSessionId = :sessionId " +
            "AND r.roundDate >= :date " +
            "ORDER BY r.roundDate ASC")
    List<AttendanceRound> findBySession_SessionIdAndRoundDateAfterOrEqual(
            @Param("sessionId") UUID sessionId,
            @Param("date") LocalDate date);
}
