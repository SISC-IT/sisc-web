package org.sejongisc.backend.attendance.repository;

import org.sejongisc.backend.attendance.entity.SessionUser;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionUserRepository extends JpaRepository<SessionUser, UUID> {
    Optional<SessionUser> findByAttendanceSession_AttendanceSessionIdAndUser_UserId(UUID sessionId, UUID userId);


    boolean existsByAttendanceSession_AttendanceSessionIdAndUser_UserId(UUID sessionId, UUID userId);

    void deleteByAttendanceSession_AttendanceSessionIdAndUser_UserId(UUID sessionId, UUID userId);

    List<SessionUser> findByAttendanceSession_AttendanceSessionId(UUID sessionId);




    /**
     * 세션에 특정 사용자가 참여하는지 확인
     */

    /**
     * 세션의 참여자 수
     */



}
