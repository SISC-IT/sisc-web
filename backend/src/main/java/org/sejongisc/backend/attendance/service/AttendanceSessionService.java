package org.sejongisc.backend.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.SessionRole;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.entity.SessionUser;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.attendance.repository.SessionUserRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AttendanceSessionService {

  private final AttendanceSessionRepository attendanceSessionRepository;
  private final UserRepository userRepository;
  private final SessionUserRepository sessionUserRepository;
  private final AttendanceAuthorizationService attendanceAuthorizationService;

  /**
   * 출석 세션 생성 - 세션 생성자(creatorId) 정보는 추후 활용 가능
   */
  @Transactional
  public void createSession(UUID creatorId, AttendanceSessionRequest request) {

    // 출석 세션 엔티티 생성
    AttendanceSession attendanceSession = AttendanceSession.builder()
        .title(request.title())
        .description(request.description())
        .allowedMinutes(request.allowedMinutes())
        .status(SessionStatus.OPEN)
        .build();

    AttendanceSession saved = attendanceSessionRepository.save(attendanceSession);

    User creator = userRepository.findById(creatorId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    // 세션 생성자를 OWNER로 세션 사용자에 추가
    SessionUser su = SessionUser.builder()
        .attendanceSession(saved)
        .user(creator)
        .sessionRole(SessionRole.OWNER)
        .build();

    sessionUserRepository.save(su);
    log.info("출석 세션 생성 완료: 세션 ID={}, 생성자 ID={}", saved.getAttendanceSessionId(), creatorId);
  }

  /**
   * 세션 ID로 상세 정보 조회
   */
  @Transactional(readOnly = true)
  public AttendanceSessionResponse getSessionById(UUID sessionId, UUID userId) {
    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

    SessionRole role = null;
    if (userId != null) {
      role = attendanceAuthorizationService.getSessionRole(sessionId, userId);
    }

    return AttendanceSessionResponse.from(session, role);
  }

  /**
   * 모든 세션 목록 조회
   */
  @Transactional(readOnly = true)
  public List<AttendanceSessionResponse> getAllSessions() {
    List<AttendanceSession> sessions = attendanceSessionRepository.findAll();

    return sessions.stream()
        .map(AttendanceSessionResponse::from)
        .toList();
  }

  /**
   * 활성 세션 목록 조회 - 현재 체크인 가능한 세션들만 필터링 (라운드 기반) - 세션의 상태가 OPEN인 세션들만 반환
   */
  @Transactional(readOnly = true)
  public List<AttendanceSessionResponse> getActiveSessions() {
    List<AttendanceSession> allSessions = attendanceSessionRepository.findAll();

    return allSessions.stream()
        .filter(session -> session.getStatus() == SessionStatus.OPEN)
        .map(AttendanceSessionResponse::from)
        .toList();
  }

  /**
   * 세션 정보 수정(세션 관리자용)
   */
  public void updateSession(UUID sessionId, AttendanceSessionRequest request, UUID userId) {
    log.info("출석 세션 수정 시작: 세션ID={}", sessionId);
    // 권한 확인
    attendanceAuthorizationService.ensureAdmin(sessionId, userId);

    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

    session = session.toBuilder()
        .title(request.title())
        .description(request.description())
        .allowedMinutes(request.allowedMinutes())
        .build();

    attendanceSessionRepository.save(session);

    log.info("출석 세션 수정 완료: 세션ID={}", sessionId);
  }

  /**
   * 세션 완전 삭제(관리자 용) - CASCADE 관련 출석 기록도 함께 삭제 - 주의: 복구 불가능
   */
  public void deleteSession(UUID sessionId, UUID userId) {
    log.info("출석 세션 삭제 시작: 세션ID={}", sessionId);
    // 권한 확인
    attendanceAuthorizationService.ensureAdmin(sessionId, userId);

    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

    attendanceSessionRepository.delete(session);

    log.info("출석 세션 삭제 완료: 세션ID={}", sessionId);
  }

  /**
   * 세션 수동 종료(해당 세션 관리자용) - 세션 상태를 CLOSED로 변경 - 체크인 비활성화
   */
  public void closeSession(UUID sessionId, UUID userId) {
    attendanceAuthorizationService.ensureAdmin(sessionId, userId);
    log.info("출석 세션 종료 시작: 세션ID={}", sessionId);

    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

    session = session.toBuilder()
        .status(SessionStatus.CLOSED)
        .build();

    attendanceSessionRepository.save(session);

    log.info("출석 세션 종료 완료: 세션ID={}", sessionId);
  }
}
