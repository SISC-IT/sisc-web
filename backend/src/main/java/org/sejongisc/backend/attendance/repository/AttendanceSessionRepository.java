package org.sejongisc.backend.attendance.repository;

import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.entity.SessionVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    // 출석 코드로 세션 찾기 (학생 출석 체크)
    Optional<AttendanceSession> findByCode(String code);

    // 태그별 세션 조회
    List<AttendanceSession> findByTag(String tag);

    // 상태별 세션 조회
    List<AttendanceSession> findByStatus(SessionStatus status);

    // 태그와 상태로 세션 조회
    List<AttendanceSession> findByTagAndStatus(String tag, SessionStatus status);

    // 모든 세션을 최신순으로 조회 (관리자용)
    List<AttendanceSession> findAllByOrderByStartsAtDesc();

    // 공개 세션만 조회
    List<AttendanceSession> findByVisibilityOrderByStartsAtDesc(SessionVisibility visibility);

    // 코드 중복 체크
    boolean existsByCode(String code);
}
