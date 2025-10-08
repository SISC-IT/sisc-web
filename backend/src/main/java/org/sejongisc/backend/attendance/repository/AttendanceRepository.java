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

    /**
 * Retrieve attendance records for the given session ordered by check-in time from oldest to newest.
 *
 * @param attendanceSession the attendance session whose records to retrieve
 * @return a list of Attendance entities for the specified session ordered by `checkedAt` ascending
 */
    List<Attendance> findByAttendanceSessionOrderByCheckedAtAsc(AttendanceSession attendanceSession);

    /**
 * Retrieves attendance records for the specified user ordered by `checkedAt` from newest to oldest.
 *
 * @param user the user whose attendance history to retrieve
 * @return a list of Attendance records for the user ordered by `checkedAt` descending
 */
    List<Attendance> findByUserOrderByCheckedAtDesc(User user);

    /**
 * Checks whether an attendance record exists for the specified attendance session and user.
 *
 * @param attendanceSession the attendance session to check
 * @param user the user to check for an existing attendance record
 * @return `true` if an Attendance exists for the session and user, `false` otherwise
 */
    boolean existsByAttendanceSessionAndUser(AttendanceSession attendanceSession, User user);

    /**
 * Retrieves all attendance records for the specified attendance session.
 *
 * @param attendanceSession the attendance session to filter records by
 * @return a list of Attendance entities for the specified session; empty if none are found
 */
    List<Attendance> findByAttendanceSession(AttendanceSession attendanceSession);

    /**
 * Finds the attendance record for a given attendance session and user.
 *
 * @param attendanceSession the attendance session to search within
 * @param user the user whose attendance record to find
 * @return an Optional containing the Attendance if present, or empty if no record exists
 */
    Optional<Attendance> findByAttendanceSessionAndUser(AttendanceSession attendanceSession, User user);

    /**
 * Finds the attendance record for the given session and user identified by userId.
 *
 * @param attendanceSession the attendance session to search
 * @param userId            the UUID of the user
 * @return                  an Optional containing the Attendance for the specified session and userId, or empty if none exists
 */
    Optional<Attendance> findByAttendanceSessionAndUser_UserId(AttendanceSession attendanceSession, UUID userId);

    /**
                                             * Retrieve attendance records whose `checkedAt` timestamp falls within the given date-time range (inclusive).
                                             *
                                             * @param startDate the start of the range (inclusive)
                                             * @param endDate   the end of the range (inclusive)
                                             * @return          the list of matching Attendance entities with `checkedAt` between `startDate` and `endDate`
                                             */
    @Query("SELECT a FROM Attendance a WHERE a.checkedAt BETWEEN :startDate AND :endDate")
    List<Attendance> findByCheckedAtBetween(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    /**
 * Finds attendance records that have the specified attendance status.
 *
 * @return a list of Attendance entities with the specified status, or an empty list if none exist
 */
    List<Attendance> findByAttendanceStatus(AttendanceStatus attendanceStatus);

    /**
     * Counts Attendance records for the specified attendance session.
     *
     * @param session the attendance session to count records for
     * @return the number of Attendance records associated with the given session
     */
    @Query("SELECT COUNT(a) FROM Attendance  a WHERE a.attendanceSession = :session")
    Long countByAttendanceSession(@Param("session") AttendanceSession session);

    /**
                                            * Count attendance records for a session with a specific status.
                                            *
                                            * @param session the attendance session to filter by
                                            * @param status  the attendance status to filter by
                                            * @return        the number of Attendance records matching the given session and status
                                            */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.attendanceSession = :session AND a.attendanceStatus = :status")
    Long countByAttendanceSessionAndStatus(@Param("session") AttendanceSession session,
                                           @Param("status") AttendanceStatus status);

}