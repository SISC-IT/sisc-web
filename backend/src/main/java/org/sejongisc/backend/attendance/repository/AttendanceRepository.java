package org.sejongisc.backend.attendance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.attendance.entity.Attendance;
import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    boolean existsByUserAndAttendanceRound( User user, AttendanceRound round);

    boolean existsByUser_UserIdAndAttendanceRound_RoundId(UUID userId, UUID roundId);
    List<Attendance> findByAttendanceRound_RoundId(UUID roundId);
    void deleteAllByAttendanceRound_AttendanceSession_AttendanceSessionIdAndUser_UserId(
        UUID sessionId,
        UUID userId
    );

    // 사용자별 출석 이력
    List<Attendance> findByUserOrderByCheckedAtDesc(User user);

    // 라운드별 특정 사용자 출석 확인
    @Query("SELECT a FROM Attendance a WHERE a.attendanceRound.roundId = :roundId AND a.user = :user")
    Optional<Attendance> findByAttendanceRound_RoundIdAndUser(@Param("roundId") UUID roundId, @Param("user") User user);
}
