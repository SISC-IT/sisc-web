package org.sejongisc.backend.attendance.repository;

import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    // 출석 코드로 세션 찾기 (학생 출석 체크)
    Optional<AttendanceSession> findByCode(String code);

    // 상태별 세션 조회
    List<AttendanceSession> findByStatus(SessionStatus status);

    // 모든 세션을 최신순으로 조회 (관리자용)
    List<AttendanceSession> findAllByOrderByStartsAtDesc();

    // 코드 중복 체크
    boolean existsByCode(String code);
}
