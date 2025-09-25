package org.sejongisc.backend.attendance.repository;

import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    // 출석 코드로 세션 찾기 (학생 출석 체크)
    Optional<AttendanceSession> findByCode(String code);

    // 관리자 세션 목록
}
