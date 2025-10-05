package org.sejongisc.backend.attendance.repository;

import org.sejongisc.backend.attendance.entity.Attendance;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.AttendanceStatus;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    // 세션별 출석자 목록 조회
    List<Attendance> findByAttendanceSessionOrderByCheckedAtAsc(AttendanceSession attendanceSession);

    // 사용자별 출석 이력
    List<Attendance> findByUserOrderByCheckedAtDesc(User user);

    // 중복 출석 방지
    boolean existsByAttendanceSessionAndUser(AttendanceSession attendanceSession, User user);

    // 세션별 모든 출석 기록 조회
    List<Attendance> findByAttendanceSession(AttendanceSession attendanceSession);

    // 특정 사용자의 세션 출석 기록 조회
    Optional<Attendance> findByAttendanceSessionAndUser(AttendanceSession attendanceSession, User user);

    // 세션과 사용자 ID로 출석 기록 조회
    Optional<Attendance> findByAttendanceSessionAndUser_UserId(AttendanceSession attendanceSession, UUID userId);

    // 특정 기간의 출석 기록 조회
    @Query("SELECT a FROM Attendance a WHERE a.checkedAt BETWEEN :startDate AND :endDate")
    List<Attendance> findByCheckedAtBetween(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    // 특정 상태의 출석 기록 조회
    List<Attendance> findByAttendanceStatus(AttendanceStatus attendanceStatus);

    // 세션별 출석 통계
    @Query("SELECT COUNT(a) FROM Attendance  a WHERE a.attendanceSession = :session")
    Long countByAttendanceSession(@Param("session") AttendanceSession session);

    // 세션별 상태별 출석 통계
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.attendanceSession = :session AND a.attendanceStatus = :status")
    Long countByAttendanceSessionAndStatus(@Param("session") AttendanceSession session,
                                           @Param("status") AttendanceStatus status);

}
