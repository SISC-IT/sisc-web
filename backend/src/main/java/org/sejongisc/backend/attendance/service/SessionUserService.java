package org.sejongisc.backend.attendance.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.SessionUserResponse;
import org.sejongisc.backend.attendance.entity.*;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.attendance.repository.SessionUserRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SessionUserService {

  private final SessionUserRepository sessionUserRepository;
  private final AttendanceSessionRepository attendanceSessionRepository;
  private final AttendanceRoundRepository attendanceRoundRepository;
  private final AttendanceRepository attendanceRepository;
  private final UserRepository userRepository;

  private final AttendanceAuthorizationService authorizationService;

  /**
   * 세션에 사용자 추가 (OWNER 전용 추천)
   */
  public SessionUserResponse addUserToSession(UUID sessionId, UUID targetUserId, UUID actorUserId) {
    log.info("세션 사용자 추가: sessionId={}, targetUserId={}, actorUserId={}", sessionId, targetUserId, actorUserId);

    authorizationService.ensureOwner(sessionId, actorUserId);

    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

    User user = userRepository.findById(targetUserId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    boolean exists = sessionUserRepository
        .existsByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId, targetUserId);

    if (exists) {
      throw new CustomException(ErrorCode.ALREADY_JOINED);
    }

    SessionUser sessionUser = SessionUser.builder()
        .attendanceSession(session)
        .user(user)
        .sessionRole(SessionRole.PARTICIPANT)
        .build();

    SessionUser saved = sessionUserRepository.save(sessionUser);

    createAbsentForPastRounds(sessionId, user);

    return SessionUserResponse.from(saved);
  }

  /**
   * 세션에서 사용자 제거 (OWNER 전용 추천) - SessionUser 삭제 - 해당 유저의 이 세션 관련 Attendance 삭제
   */
  public void removeUserFromSession(UUID sessionId, UUID targetUserId, UUID actorUserId) {
    log.info("세션 사용자 제거: sessionId={}, targetUserId={}, actorUserId={}", sessionId, targetUserId, actorUserId);

    authorizationService.ensureOwner(sessionId, actorUserId);

    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

    // SessionUser 삭제
    sessionUserRepository.deleteByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId, targetUserId);

    // 해당 세션의 라운드들에서 targetUserId의 출석 레코드 삭제
    attendanceRepository.deleteAllByAttendanceRound_AttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId,
        targetUserId);
  }

  /**
   * 세션 참여자 조회 (멤버면 조회 가능 / 또는 공개)
   */
  @Transactional(readOnly = true)
  public List<SessionUserResponse> getSessionUsers(UUID sessionId, UUID viewerUserId) {
    authorizationService.ensureMember(sessionId, viewerUserId);

    List<SessionUser> users = sessionUserRepository
        .findByAttendanceSession_AttendanceSessionId(sessionId);

    return users.stream().map(SessionUserResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public boolean isUserInSession(UUID sessionId, UUID userId) {
    return sessionUserRepository.existsByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId, userId);
  }

  private void createAbsentForPastRounds(UUID sessionId, User user) {
    List<AttendanceRound> pastRounds = attendanceRoundRepository
        .findByAttendanceSession_AttendanceSessionIdAndRoundDateBefore(sessionId, LocalDate.now());

    for (AttendanceRound round : pastRounds) {
      boolean already = attendanceRepository.findByAttendanceRound_RoundIdAndUser(round.getRoundId(), user).isPresent();
      if (already) {
        continue;
      }

      Attendance absent = Attendance.builder()
          .user(user)
          .attendanceRound(round)
          .attendanceStatus(AttendanceStatus.ABSENT)
          .note("세션 중간 참여 - 이전 라운드는 자동 결석 처리")
          .build();

      attendanceRepository.save(absent);
    }
  }

  /**
   * 세션 가입
   */
  @Transactional
  public void joinSession(UUID sessionId, UUID userId) {
    // 이미 가입했는지 체크
    boolean exists = sessionUserRepository.existsByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId,
        userId);
    if (exists) {
      throw new CustomException(ErrorCode.ALREADY_JOINED);
    }

    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    sessionUserRepository.save(SessionUser.builder()
        .attendanceSession(session)
        .user(user)
        .sessionRole(SessionRole.PARTICIPANT)
        .build());
  }

  /**
   * 세션 탈퇴
   */
  @Transactional
  public void leaveSession(UUID sessionId, UUID userId) {
    SessionUser su = sessionUserRepository
        .findByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId, userId)
        .orElseThrow(() -> new CustomException(ErrorCode.NOT_SESSION_MEMBER));

    sessionUserRepository.delete(su);
  }

  /**
   * 세션 관리자 추가/제거
   */
  @Transactional
  public void addAdmin(UUID sessionId, UUID targetUserId) {
    SessionUser su = sessionUserRepository
        .findByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId, targetUserId)
        .orElseThrow(() -> new CustomException(ErrorCode.TARGET_NOT_SESSION_MEMBER));
    su.changeRole(SessionRole.MANAGER);
  }

  @Transactional
  public void removeAdmin(UUID sessionId, UUID targetUserId) {
    SessionUser su = sessionUserRepository
        .findByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId, targetUserId)
        .orElseThrow(() -> new CustomException(ErrorCode.TARGET_NOT_SESSION_MEMBER));

    // OWNER를 강제로 내릴지 여부는 정책
    if (su.getSessionRole() == SessionRole.OWNER) {
      throw new CustomException(ErrorCode.CANNOT_DEMOTE_OWNER);
    }
    su.changeRole(SessionRole.PARTICIPANT);
  }
}
