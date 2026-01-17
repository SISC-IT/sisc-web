package org.sejongisc.backend.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceRoundRequest;
import org.sejongisc.backend.attendance.dto.AttendanceRoundResponse;
import org.sejongisc.backend.attendance.entity.*;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.attendance.repository.SessionUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 출석 라운드 서비스
 * 세션 내 주차별 라운드 관리
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AttendanceRoundService {

    private final AttendanceRoundRepository attendanceRoundRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final AttendanceRepository attendanceRepository;


    /**
     * 라운드 생성
     */
    public AttendanceRoundResponse createRound(UUID sessionId, AttendanceRoundRequest request) {
        log.info("📋 라운드 생성 요청: sessionId={}, roundDate={}, startTime={}, allowedMinutes={}",
                sessionId, request.getRoundDate(), request.getStartTime(), request.getAllowedMinutes());

        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId));

        try {
            // 클라이언트가 보낸 날짜 대신 서버의 현재 날짜를 사용하여 시간대 차이 방지
            LocalDate roundDate = request.getRoundDate();
            if (roundDate == null) {
                roundDate = LocalDate.now();
            }
            LocalTime requestStartTime = request.getStartTime();

            log.info("📅 시간대 정보: 클라이언트 roundDate={}, 서버 today={}, 요청 startTime={}",
                    request.getRoundDate(), roundDate, requestStartTime);

            AttendanceRound round = AttendanceRound.builder()
                    .attendanceSession(session)
                    .roundDate(roundDate)
                    .roundStatus(RoundStatus.UPCOMING)
                    .build();



            AttendanceRound saved = attendanceRoundRepository.save(round);
            session.getRounds().add(saved);
            // 양방향 관계를 DB에 반영하기 위해 세션도 저장
            attendanceSessionRepository.save(session);

            // ⭐ 라운드 생성 시 세션의 모든 SessionUser에 대해 PENDING 상태의 Attendance 미리 생성
            log.info("📝 세션 사용자에 대한 PENDING 출석 기록 생성 시작: sessionId={}, roundId={}",
                    sessionId, saved.getRoundId());

            List<SessionUser> sessionUsers = sessionUserRepository.findBySessionId(sessionId);
            for (SessionUser sessionUser : sessionUsers) {
                Attendance pendingAttendance = Attendance.builder()
                        .user(sessionUser.getUser())
                        .attendanceRound(saved)
                        .attendanceStatus(AttendanceStatus.PENDING)
                        .build();
                attendanceRepository.save(pendingAttendance);
                log.info("  ✓ PENDING 출석 기록 생성: userId={}, userName={}, roundId={}",
                        sessionUser.getUser().getUserId(), sessionUser.getUser().getName(), saved.getRoundId());
            }

            log.info("✅ 라운드 생성 완료 - sessionId: {}, roundId: {}, roundDate: {}, roundStatus: {}, 생성된PENDING개수: {}",
                    sessionId, saved.getRoundId(), saved.getRoundDate(), saved.getRoundStatus(), sessionUsers.size());
            return AttendanceRoundResponse.fromEntity(saved);
        } catch (Exception e) {
            log.error("❌ 라운드 생성 중 오류 발생: sessionId={}, error={}", sessionId, e.getMessage(), e);
            throw new RuntimeException("라운드 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 라운드 조회 (개별)
     */
    @Transactional(readOnly = true)
    public AttendanceRoundResponse getRound(UUID roundId) {
        AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
                .orElseThrow(() -> new IllegalArgumentException("라운드를 찾을 수 없습니다: " + roundId));

        return AttendanceRoundResponse.fromEntity(round);
    }

    /**
     * 세션 내 라운드 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AttendanceRoundResponse> getRoundsBySession(UUID sessionId) {
        List<AttendanceRound> rounds = attendanceRoundRepository
                .findByAttendanceSession_AttendanceSessionIdOrderByRoundDateAsc(sessionId);

        return rounds.stream()
                .map(AttendanceRoundResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 라운드 정보 수정
     */
    public AttendanceRoundResponse updateRound(UUID roundId, AttendanceRoundRequest request) {
        AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
                .orElseThrow(() -> new IllegalArgumentException("라운드를 찾을 수 없습니다: " + roundId));
        

        AttendanceRound updated = attendanceRoundRepository.save(round);
        log.info("라운드 수정 완료 - roundId: {}", roundId);
        return AttendanceRoundResponse.fromEntity(updated);
    }

    /**
     * 라운드 삭제
     */
    public void deleteRound(UUID roundId) {
        AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
                .orElseThrow(() -> new IllegalArgumentException("라운드를 찾을 수 없습니다: " + roundId));

        AttendanceSession session = round.getAttendanceSession();
        session.getRounds().remove(round);

        attendanceRoundRepository.delete(round);
        log.info("라운드 삭제 완료 - roundId: {}", roundId);
    }

    /**
     * 특정 날짜의 라운드 조회
     */
    @Transactional(readOnly = true)
    public AttendanceRoundResponse getRoundByDate(UUID sessionId, LocalDate date) {
        AttendanceRound round = attendanceRoundRepository
                .findByAttendanceSession_AttendanceSessionIdAndRoundDate(sessionId, date)
                .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 라운드를 찾을 수 없습니다"));

        return AttendanceRoundResponse.fromEntity(round);
    }
}
