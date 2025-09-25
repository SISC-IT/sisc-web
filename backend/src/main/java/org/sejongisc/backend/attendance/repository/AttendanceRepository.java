package org.sejongisc.backend.attendance.repository;

import org.sejongisc.backend.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    // 세션별 출석자 목록 조회
    List<Attendance> findByAttendanceSessionIdOrderByCheckedAtAsc(UUID sessionId);

    // 사용자별 출석 이력
    List<Attendance> findByUserIdOrderByCheckedAtDesc(UUID userId);

    // 중복 출석 방지
    boolean existsByAttendanceSessionIdAndUserId(UUID sessionId, UUID userId);
}
