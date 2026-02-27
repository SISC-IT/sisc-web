package org.sejongisc.backend.attendance.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.attendance.entity.SessionRole;
import org.sejongisc.backend.attendance.entity.SessionUser;
import org.sejongisc.backend.attendance.repository.SessionUserRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 출석 권한 관리 서비스
 */
@Service
@RequiredArgsConstructor
public class AttendanceAuthorizationService {
  private final SessionUserRepository sessionUserRepository;

  public void ensureAuthenticated(UUID userId) {
    if (userId == null) {
      throw new CustomException(ErrorCode.UNAUTHENTICATED);
    }
  }

  public SessionRole getSessionRole(UUID sessionId, UUID userId) {
    if (userId == null) return null;
    return sessionUserRepository
        .findByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId, userId)
        .map(SessionUser::getSessionRole)
        .orElse(null);
  }

  public SessionRole requireRole(UUID sessionId, UUID userId) {
    ensureAuthenticated(userId);
    return sessionUserRepository
        .findByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId, userId)
        .map(SessionUser::getSessionRole)
        .orElseThrow(() -> new CustomException(ErrorCode.NOT_SESSION_MEMBER));
  }

  public void ensureMember(UUID sessionId, UUID userId) {
    requireRole(sessionId, userId);
  }

  public void ensureAdmin(UUID sessionId, UUID userId) {
    SessionRole role = requireRole(sessionId, userId);
    if (role != SessionRole.MANAGER && role != SessionRole.OWNER) {
      throw new CustomException(ErrorCode.NOT_SESSION_ADMIN);
    }
  }

  public void ensureOwner(UUID sessionId, UUID userId) {
    SessionRole role = requireRole(sessionId, userId);
    if (role != SessionRole.OWNER) {
      throw new CustomException(ErrorCode.NOT_SESSION_OWNER);
    }
  }
}