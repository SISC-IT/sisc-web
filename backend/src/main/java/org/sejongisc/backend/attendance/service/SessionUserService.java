package org.sejongisc.backend.attendance.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.sessionUser.*;
import org.sejongisc.backend.attendance.entity.*;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.attendance.repository.SessionUserRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.entity.UserStatus;
import org.sejongisc.backend.user.repository.UserRepository;
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

  public void addAllUsers(UUID sessionId, UUID userId) {
    // 권한 확인
    authorizationService.ensureOwner(sessionId, userId);
    log.info("세션에 모든 사용자 추가 시작: 세션ID={}", sessionId);

    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

    // UserStatus.ACTIVE인 사용자만 추가하도록 수정
    List<User> allUsers = userRepository.findAllByStatus(UserStatus.ACTIVE);

    for (User user : allUsers) {
      boolean alreadyAdded = sessionUserRepository.existsByAttendanceSessionAndUser(session, user);
      if (!alreadyAdded) {
        SessionUser su = SessionUser.builder()
            .attendanceSession(session)
            .user(user)
            .sessionRole(SessionRole.PARTICIPANT)
            .build();
        sessionUserRepository.save(su);
        log.info("사용자 {} 세션에 추가됨", user.getUserId());
      }
    }

    log.info("세션에 모든 사용자 추가 완료. 세션 ID : {}", sessionId);
  }

  /**
   * 세션에 사용자 추가 (OWNER 전용 추천)
   */
  public SessionUserResponse addUserToSession(UUID sessionId, UUID targetUserId, UUID actorUserId) {
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
    log.info("세션 사용자 추가: sessionId={}, targetUserId={}, actorUserId={}", sessionId, targetUserId, actorUserId);

    return SessionUserResponse.from(saved);
  }

  /**
   * 세션에서 사용자 제거 (OWNER 전용 추천) - SessionUser 삭제 - 해당 유저의 이 세션 관련 Attendance 삭제
   */
  public void removeUserFromSession(UUID sessionId, UUID targetUserId, UUID actorUserId) {
    authorizationService.ensureOwner(sessionId, actorUserId);

    attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

    // SessionUser 삭제
    sessionUserRepository.deleteByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId, targetUserId);

    // 해당 세션의 라운드들에서 targetUserId의 출석 레코드 삭제
    attendanceRepository.deleteAllByAttendanceRound_AttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId,
        targetUserId);
    log.info("세션 사용자 제거: sessionId={}, targetUserId={}, actorUserId={}", sessionId, targetUserId, actorUserId);
  }

  /**
   * 세션 참여자 조회 (멤버면 조회 가능 / 또는 공개)
   */
  @Transactional(readOnly = true)
  public SessionAttendanceTableResponse getSessionUsers(UUID sessionId, UUID viewerUserId) {
    authorizationService.ensureMember(sessionId, viewerUserId);

    // 세션 및 모든 회차 조회
    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
    List<AttendanceRound> rounds = attendanceRoundRepository.findByAttendanceSession_AttendanceSessionIdOrderByStartAtAsc(sessionId); // roundNumber 컬럼이 있다고 가정

    // 세션의 모든 유저 조회
    List<SessionUser> sessionUsers = sessionUserRepository.findByAttendanceSession_AttendanceSessionId(sessionId);

    // 해당 세션의 모든 출석 기록 한꺼번에 조회 (N+1 방지)
    List<Attendance> allAttendances = attendanceRepository.findByAttendanceRoundIn(rounds);

    // 유저 ID별로 출석 기록 그룹화 (Map<UserId, Map<RoundId, Attendance>>)
    Map<UUID, Map<UUID, Attendance>> attendanceMap = allAttendances.stream()
        .collect(Collectors.groupingBy(
            a -> a.getUser().getUserId(),
            Collectors.toMap(a -> a.getAttendanceRound().getRoundId(), a -> a)
        ));

    // IntStream을 사용하여 인덱스 기반으로 RoundHeaderResponse 생성 (1부터 시작)
    List<RoundHeaderResponse> roundHeaders = IntStream.range(0, rounds.size())
        .mapToObj(i -> {
          AttendanceRound r = rounds.get(i);
          return new RoundHeaderResponse(r.getRoundId(), i + 1); // i + 1이 roundNumber
        })
        .toList();

    List<UserAttendanceRowResponse> userRows = sessionUsers.stream().map(su -> {
      User user = su.getUser();

      // 해당 유저의 회차별 상태 리스트 생성 (회차 순서 보장)
      List<AttendanceStatusResponse> statusList = rounds.stream().map(r -> {
        Attendance att = attendanceMap.getOrDefault(user.getUserId(), Map.of()).get(r.getRoundId());
        return new AttendanceStatusResponse(r.getRoundId(),
            att != null ? att.getAttendanceStatus().name() : "ABSENT", // 기록 없으면 미출석
            att != null ? att.getAttendanceId() : null);
      }).toList();

      return new UserAttendanceRowResponse(user.getUserId(), user.getName(), user.getStudentId(), su.getSessionRole().name(), statusList);
    }).toList();

    return new SessionAttendanceTableResponse(session.getTitle(), roundHeaders, userRows);
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
   * 세션 관리자 추가/제거
   */
  @Transactional
  public void addAdmin(UUID sessionId, UUID targetUserId, UUID actorUserId) {
    authorizationService.ensureOwner(sessionId, actorUserId);
    SessionUser su = sessionUserRepository
        .findByAttendanceSession_AttendanceSessionIdAndUser_UserId(sessionId, targetUserId)
        .orElseThrow(() -> new CustomException(ErrorCode.TARGET_NOT_SESSION_MEMBER));
    su.changeRole(SessionRole.MANAGER);
  }

  @Transactional
  public void removeAdmin(UUID sessionId, UUID targetUserId, UUID actorUserId) {
    authorizationService.ensureOwner(sessionId, actorUserId);
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
