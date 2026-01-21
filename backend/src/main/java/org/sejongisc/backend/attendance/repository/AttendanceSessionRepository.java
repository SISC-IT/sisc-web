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

    // 상태별 세션 조회
    List<AttendanceSession> findByStatus(SessionStatus status);


}
