package org.sejongisc.backend.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.SessionUserResponse;
import org.sejongisc.backend.attendance.entity.*;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.attendance.repository.SessionUserRepository;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * 세션에 사용자 추가
     * - 사용자가 이미 참여 중이면 예외 발생
     * - 세션의 이전 라운드들에 대해 자동으로 ABSENT 상태의 Attendance 레코드 생성
     *
     * 흐름:
     * 1. 세션과 사용자 존재 확인
     * 2. 중복 참여 여부 확인
     * 3. SessionUser 레코드 생성
     * 4. 이전 라운드들에 대해 결석 처리
     */
    public SessionUserResponse addUserToSession(UUID sessionId, UUID userId) {
        log.info("🔧 세션에 사용자 추가 시작: sessionId={}, userId={}", sessionId, userId);

        // 1. 세션 존재 확인
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId));

        // 2. 사용자 존재 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 3. 중복 참여 여부 확인
        if (sessionUserRepository.existsBySessionIdAndUserId(sessionId, userId)) {
            throw new IllegalArgumentException("이미 세션에 참여 중입니다: " + user.getName());
        }

        log.info("✅ 유효성 검사 완료: sessionId={}, userId={}, userName={}", sessionId, userId, user.getName());

        // 4. SessionUser 레코드 생성
        SessionUser sessionUser = SessionUser.builder()
                .attendanceSession(session)
                .user(user)
                .userName(user.getName())
                .build();

        sessionUser = sessionUserRepository.save(sessionUser);
        log.info("💾 SessionUser 저장 완료: sessionUserId={}, userName={}", sessionUser.getSessionUserId(), user.getName());

        // 5. ⭐ 핵심: 이미 진행된 라운드들에 대해 자동으로 결석 처리
        List<AttendanceRound> pastRounds = attendanceRoundRepository.findBySession_SessionIdAndRoundDateBefore(
                sessionId,
                LocalDate.now()
        );

        if (!pastRounds.isEmpty()) {
            log.info("📅 과거 라운드 자동 결석 처리: 이전 라운드 수={}", pastRounds.size());

            for (AttendanceRound round : pastRounds) {
                // 이미 해당 라운드에 출석 기록이 있는지 확인
                boolean alreadyExists = attendanceRepository.findByAttendanceRound_RoundIdAndUser(round.getRoundId(), user)
                        .isPresent();

                if (!alreadyExists) {
                    // 새로운 Attendance 레코드 생성 (결석 상태)
                    Attendance absentRecord = Attendance.builder()
                            .user(user)
                            .attendanceSession(session)
                            .attendanceRound(round)
                            .attendanceStatus(AttendanceStatus.ABSENT)
                            .note("세션 중간 참여 - 이전 라운드는 자동 결석 처리")
                            .build();

                    attendanceRepository.save(absentRecord);
                    log.info("  - 결석 기록 생성: roundId={}, date={}, userName={}",
                            round.getRoundId(), round.getRoundDate(), user.getName());
                }
            }

            log.info("✅ 과거 라운드 자동 결석 처리 완료: 처리된 라운드 수={}", pastRounds.size());
        }

        log.info("✅ 세션에 사용자 추가 완료: sessionId={}, userId={}, userName={}",
                sessionId, userId, user.getName());

        return convertToResponse(sessionUser);
    }

    /**
     * 세션에서 사용자 제거
     * - SessionUser 레코드 삭제
     * - 해당 사용자의 모든 Attendance 레코드도 함께 삭제 (관련된 모든 라운드의 출석 기록 제거)
     */
    public void removeUserFromSession(UUID sessionId, UUID userId) {
        log.info("🗑️ 세션에서 사용자 제거 시작: sessionId={}, userId={}", sessionId, userId);

        // 1. 세션 존재 확인
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId));

        // 2. 사용자 존재 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 3. SessionUser 레코드 삭제
        sessionUserRepository.deleteBySessionIdAndUserId(sessionId, userId);
        log.info("💾 SessionUser 레코드 삭제 완료: userName={}", user.getName());

        // 4. ⭐ 해당 세션의 모든 Attendance 레코드 삭제 (해당 라운드별 출석 기록 모두 제거)
        List<Attendance> attendancesToDelete = attendanceRepository.findAllBySessionAndUserId(session, userId);

        if (!attendancesToDelete.isEmpty()) {
            log.info("🗑️ Attendance 레코드 삭제 시작: 삭제 대상 수={}", attendancesToDelete.size());

            attendanceRepository.deleteAll(attendancesToDelete);

            log.info("✅ Attendance 레코드 삭제 완료: 삭제된 레코드 수={}", attendancesToDelete.size());
            for (Attendance a : attendancesToDelete) {
                log.info("  - 삭제됨: roundId={}, status={}",
                        a.getAttendanceRound() != null ? a.getAttendanceRound().getRoundId() : "null",
                        a.getAttendanceStatus());
            }
        }

        log.info("✅ 세션에서 사용자 제거 완료: sessionId={}, userId={}, userName={}",
                sessionId, userId, user.getName());
    }

    /**
     * 세션의 모든 참여자 조회
     */
    @Transactional(readOnly = true)
    public List<SessionUserResponse> getSessionUsers(UUID sessionId) {
        log.info("📋 세션 참여자 조회: sessionId={}", sessionId);

        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId));

        List<SessionUser> sessionUsers = sessionUserRepository.findBySessionId(sessionId);

        log.info("📊 세션 참여자 조회 결과: sessionId={}, 참여자 수={}",
                sessionId, sessionUsers.size());

        return sessionUsers.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 특정 사용자가 세션에 참여하는지 확인
     */
    @Transactional(readOnly = true)
    public boolean isUserInSession(UUID sessionId, UUID userId) {
        return sessionUserRepository.existsBySessionIdAndUserId(sessionId, userId);
    }

    /**
     * SessionUser를 SessionUserResponse로 변환
     */
    private SessionUserResponse convertToResponse(SessionUser sessionUser) {
        return SessionUserResponse.builder()
                .sessionUserId(sessionUser.getSessionUserId())
                .userId(sessionUser.getUser().getUserId())
                .sessionId(sessionUser.getAttendanceSession().getAttendanceSessionId())
                .userName(sessionUser.getUserName())
                .createdAt(sessionUser.getCreatedDate())
                .build();
    }
}
