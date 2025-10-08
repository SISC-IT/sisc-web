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

    /**
 * Finds the attendance session that matches the given attendance code.
 *
 * @param code the unique attendance code associated with a session
 * @return an Optional containing the matching AttendanceSession if present, empty otherwise
 */
    Optional<AttendanceSession> findByCode(String code);

    /**
 * Finds attendance sessions labeled with the specified tag.
 *
 * @param tag the tag to match on
 * @return a list of AttendanceSession entities that have the given tag; empty list if none found
 */
    List<AttendanceSession> findByTag(String tag);

    /**
 * Finds attendance sessions with the specified status.
 *
 * @param status the session status to filter by
 * @return a list of AttendanceSession entities that have the given status
 */
    List<AttendanceSession> findByStatus(SessionStatus status);

    /**
 * Finds attendance sessions that match both the specified tag and session status.
 *
 * @param tag    the tag to filter sessions by
 * @param status the session status to filter sessions by
 * @return a list of AttendanceSession entities matching the given tag and status; empty if none found
 */
    List<AttendanceSession> findByTagAndStatus(String tag, SessionStatus status);

    /**
 * Retrieve all attendance sessions ordered by start time from newest to oldest.
 *
 * @return a list of AttendanceSession objects ordered by `startsAt` in descending order
 */
    List<AttendanceSession> findAllByOrderByStartsAtDesc();

    /**
 * Retrieves attendance sessions with the specified visibility ordered by start time descending.
 *
 * @param visibility the session visibility to filter by
 * @return a list of AttendanceSession objects matching the visibility, ordered by `startsAt` from newest to oldest
 */
    List<AttendanceSession> findByVisibilityOrderByStartsAtDesc(SessionVisibility visibility);

    /**
 * Determines whether an AttendanceSession with the given code exists.
 *
 * @param code the session code to check for existence
 * @return `true` if an AttendanceSession with the specified code exists, `false` otherwise
 */
    boolean existsByCode(String code);
}