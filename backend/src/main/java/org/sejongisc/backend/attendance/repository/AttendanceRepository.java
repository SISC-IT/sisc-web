package org.sejongisc.backend.attendance.repository;

import org.sejongisc.backend.attendance.entity.Attendance;
import org.sejongisc.backend.user.entity.User;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    // 세션별 출석자 목록 조회
    List<Attendance> findByAttendanceSessionIdOrderByCheckedAtAsc(UUID sessionId);

    // 사용자별 출석 이력
    List<Attendance> findByUserIdOrderByCheckedAtDesc(UUID userId);

    // 중복 출석 방지 - UUID 기반 검사
    boolean existsByAttendanceSessionIdAndUserId(UUID sessionId, UUID userId);

    // 중복 출석 방지 - User 기반 검사
    boolean existByUserAndAttendanceSessionId(User user, UUID attendanceSessionId);

    // 세션의 모든 출석 기록 조회
    List<Attendance> findByAttendanceSessionId(UUID sessionId);

    // 특정 사용자의 세션 출석 기록 조회
    Optional<Attendance> findByAttendanceSessionIdAndUser(UUID sessionId, User user);

}
