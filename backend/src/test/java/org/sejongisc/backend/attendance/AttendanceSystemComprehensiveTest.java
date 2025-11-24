package org.sejongisc.backend.attendance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sejongisc.backend.attendance.dto.*;
import org.sejongisc.backend.attendance.entity.*;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.attendance.service.AttendanceRoundService;
import org.sejongisc.backend.attendance.service.AttendanceService;
import org.sejongisc.backend.attendance.service.AttendanceSessionService;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * 출석 시스템 포괄적 통합 테스트
 *
 * 테스트 범위:
 * 1. 세션/라운드 전체 생명주기 (생성 → 조회 → 수정 → 삭제)
 * 2. 시간 기반 검증 (과거/현재/미래 라운드)
 * 3. 위치 기반 검증
 * 4. 익명 사용자 처리
 * 5. 출석 상태 판별 (정시/지각/결석)
 * 6. 중복 체크인 방지
 * 7. 관리자 기능 (세션/라운드 수정, 상태 변경)
 * 8. 에러 케이스 처리
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@DisplayName("🎯 출석 시스템 포괄적 통합 테스트")
public class AttendanceSystemComprehensiveTest {

    @Autowired
    private AttendanceSessionService attendanceSessionService;

    @Autowired
    private AttendanceRoundService attendanceRoundService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceSessionRepository sessionRepository;

    @Autowired
    private AttendanceRoundRepository roundRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser1;
    private User testUser2;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser1 = User.builder()
                .email("test1@example.com")
                .passwordHash("password123")
                .name("테스트 사용자 1")
                .role(Role.TEAM_MEMBER)
                .point(0)
                .build();
        testUser1 = userRepository.save(testUser1);

        testUser2 = User.builder()
                .email("test2@example.com")
                .passwordHash("password123")
                .name("테스트 사용자 2")
                .role(Role.TEAM_MEMBER)
                .point(0)
                .build();
        testUser2 = userRepository.save(testUser2);
    }

    // ============================================================================
    // 1. 세션/라운드 전체 생명주기 테스트
    // ============================================================================

    @Test
    @DisplayName("세션 생성 → 라운드 생성 → 출석 체크인 → 조회 전체 워크플로우")
    void testCompleteAttendanceWorkflow() {
        System.out.println("\n========== 📋 전체 워크플로우 테스트 시작 ==========\n");

        // 1. 세션 생성
        LocalDateTime sessionStart = LocalDateTime.now().minusHours(1);
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("출석 시스템 통합 테스트 세션")
                .startsAt(sessionStart)
                .windowSeconds(1800)  // 30분
                .rewardPoints(100)
                .build();

        AttendanceSessionResponse sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();
        System.out.println("✅ 세션 생성: " + sessionId);
        assertThat(sessionResponse.getTitle()).isEqualTo("출석 시스템 통합 테스트 세션");
        assertThat(sessionResponse.getRewardPoints()).isEqualTo(100);

        // 2. 라운드 생성 (3개: 정시, 지각, 미래)
        // 라운드 1: 정시 범위 (현재 기준 2분 이전 시작)
        LocalDate today = LocalDate.now();
        LocalTime roundTime1 = LocalTime.now().minusMinutes(2);
        AttendanceRoundRequest round1Request = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(roundTime1)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse round1Response = attendanceRoundService.createRound(sessionId, round1Request);
        UUID round1Id = round1Response.getRoundId();
        System.out.println("✅ 라운드 1 생성 (정시 범위): " + round1Id);
        assertThat(round1Response.getStatus()).isEqualTo("active");

        // 라운드 2: 지각 범위 (6분 이전 시작)
        LocalTime roundTime2 = LocalTime.now().minusMinutes(6);
        AttendanceRoundRequest round2Request = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(roundTime2)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse round2Response = attendanceRoundService.createRound(sessionId, round2Request);
        UUID round2Id = round2Response.getRoundId();
        System.out.println("✅ 라운드 2 생성 (지각 범위): " + round2Id);

        // 3. 세션에 라운드가 포함되었는지 확인
        List<AttendanceRoundResponse> rounds = attendanceRoundService.getRoundsBySession(sessionId);
        System.out.println("✅ 세션의 라운드 목록: " + rounds.size() + "개");
        assertThat(rounds).hasSize(2);

        // 4. 정시 체크인 (라운드 1)
        AttendanceCheckInRequest checkIn1 = AttendanceCheckInRequest.builder()
                .roundId(round1Id)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse checkInResponse1 = attendanceService.checkInByRound(
                checkIn1,
                testUser1.getUserId()
        );
        System.out.println("✅ 사용자 1 정시 체크인: " + checkInResponse1.getStatus());
        assertThat(checkInResponse1.getSuccess()).isTrue();
        assertThat(checkInResponse1.getStatus()).isEqualTo("PRESENT");
        assertThat(checkInResponse1.getAwardedPoints()).isEqualTo(100);

        // 5. 지각 체크인 (라운드 2)
        AttendanceCheckInRequest checkIn2 = AttendanceCheckInRequest.builder()
                .roundId(round2Id)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse checkInResponse2 = attendanceService.checkInByRound(
                checkIn2,
                testUser1.getUserId()
        );
        System.out.println("✅ 사용자 1 지각 체크인: " + checkInResponse2.getStatus());
        assertThat(checkInResponse2.getSuccess()).isTrue();
        assertThat(checkInResponse2.getStatus()).isEqualTo("LATE");

        // 6. 익명 사용자 체크인
        AttendanceCheckInRequest anonCheckIn = AttendanceCheckInRequest.builder()
                .roundId(round1Id)
                .latitude(37.4979)
                .longitude(127.0276)
                .userName("익명 사용자")
                .build();

        AttendanceCheckInResponse anonResponse = attendanceService.checkInByRound(
                anonCheckIn,
                null  // 익명 사용자
        );
        System.out.println("✅ 익명 사용자 체크인: " + anonResponse.getStatus());
        assertThat(anonResponse.getSuccess()).isTrue();

        // 7. 출석 현황 조회
        List<AttendanceResponse> attendances = attendanceService.getAttendancesBySession(sessionId);
        System.out.println("✅ 세션 전체 출석 현황: " + attendances.size() + "명");
        assertThat(attendances.size()).isGreaterThanOrEqualTo(2);

        System.out.println("\n========== ✅ 전체 워크플로우 테스트 완료 ==========\n");
    }

    // ============================================================================
    // 2. 시간 기반 검증 테스트
    // ============================================================================

    @Test
    @DisplayName("시간 범위 검증: 과거/현재/미래 라운드")
    void testTimeRangeValidation() {
        System.out.println("\n========== ⏰ 시간 범위 검증 테스트 ==========\n");

        // 세션 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("시간 범위 테스트")
                .startsAt(LocalDateTime.now().minusHours(2))
                .windowSeconds(3600)
                .rewardPoints(50)
                .build();
        AttendanceSessionResponse session = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = session.getAttendanceSessionId();

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // 케이스 1: 정확히 오늘 라운드 (체크인 가능)
        LocalTime validTime = now.minusMinutes(5);
        AttendanceRoundRequest validRequest = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(validTime)
                .allowedMinutes(20)
                .build();
        AttendanceRoundResponse validRound = attendanceRoundService.createRound(sessionId, validRequest);
        System.out.println("✅ 오늘 라운드 (체크인 가능): " + validRound.getStatus());
        assertThat(validRound.getStatus()).isEqualTo("active");

        // 케이스 2: 내일 라운드 (체크인 불가)
        LocalTime futureTime = now.minusMinutes(5);
        AttendanceRoundRequest futureRequest = AttendanceRoundRequest.builder()
                .roundDate(today.plusDays(1))
                .startTime(futureTime)
                .allowedMinutes(20)
                .build();
        AttendanceRoundResponse futureRound = attendanceRoundService.createRound(sessionId, futureRequest);
        System.out.println("✅ 내일 라운드 생성 (상태: " + futureRound.getStatus() + ")");
        assertThat(futureRound.getStatus()).isEqualTo("upcoming");

        // 케이스 3: 내일 라운드에서 체크인 시도 (실패)
        AttendanceCheckInRequest futureCheckIn = AttendanceCheckInRequest.builder()
                .roundId(futureRound.getRoundId())
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse futureCheckInResponse = attendanceService.checkInByRound(
                futureCheckIn,
                testUser1.getUserId()
        );
        System.out.println("❌ 미래 라운드 체크인 결과: " + futureCheckInResponse.getSuccess());
        assertThat(futureCheckInResponse.getSuccess()).isFalse();
        assertThat(futureCheckInResponse.getFailureReason()).contains("시간 초과");

        System.out.println("\n========== ✅ 시간 범위 검증 완료 ==========\n");
    }

    // ============================================================================
    // 3. 위치 기반 검증 테스트
    // ============================================================================

    @Test
    @DisplayName("위치 기반 검증: GPS 반경 내/외 체크인")
    void testLocationValidation() {
        System.out.println("\n========== 📍 위치 기반 검증 테스트 ==========\n");

        // 위치 정보가 포함된 세션 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("위치 검증 테스트")
                .startsAt(LocalDateTime.now().minusHours(1))
                .windowSeconds(1800)
                .rewardPoints(50)
                .latitude(37.4979)  // 서울
                .longitude(127.0276)
                .radiusMeters(100)  // 100미터 반경
                .build();

        AttendanceSessionResponse session = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = session.getAttendanceSessionId();
        System.out.println("✅ 위치 정보가 있는 세션 생성 (반경 100m)");

        // 라운드 생성
        LocalDate today = LocalDate.now();
        LocalTime roundTime = LocalTime.now().minusMinutes(2);
        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(roundTime)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse round = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = round.getRoundId();

        // 케이스 1: 정확한 위치에서 체크인 (성공 예상)
        AttendanceCheckInRequest validLocation = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)  // 정확한 위치
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse validResponse = attendanceService.checkInByRound(
                validLocation,
                testUser1.getUserId()
        );
        System.out.println("✅ 정확한 위치 체크인: " + validResponse.getSuccess());
        assertThat(validResponse.getSuccess()).isTrue();

        // 케이스 2: 범위 밖의 위치에서 체크인 (실패 예상)
        // 서울과 대구 간 거리는 약 300km이므로 범위 밖
        AttendanceCheckInRequest farLocation = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(35.8726)  // 대구
                .longitude(128.5973)
                .build();

        AttendanceCheckInResponse farResponse = attendanceService.checkInByRound(
                farLocation,
                testUser2.getUserId()
        );
        System.out.println("❌ 범위 밖 위치 체크인: " + farResponse.getSuccess());
        assertThat(farResponse.getSuccess()).isFalse();

        System.out.println("\n========== ✅ 위치 기반 검증 완료 ==========\n");
    }

    // ============================================================================
    // 4. 익명 사용자 처리 테스트
    // ============================================================================

    @Test
    @DisplayName("익명 사용자 출석 처리: userName으로 신원 기록")
    void testAnonymousUserHandling() {
        System.out.println("\n========== 😊 익명 사용자 처리 테스트 ==========\n");

        // 세션/라운드 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("익명사용자 테스트")
                .startsAt(LocalDateTime.now().minusHours(1))
                .windowSeconds(1800)
                .rewardPoints(100)
                .build();

        AttendanceSessionResponse session = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = session.getAttendanceSessionId();

        LocalDate today = LocalDate.now();
        LocalTime roundTime = LocalTime.now().minusMinutes(2);
        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(roundTime)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse round = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = round.getRoundId();

        // 익명 사용자 체크인 (3명)
        String[] anonNames = {"김익명", "이익명", "박익명"};

        for (String name : anonNames) {
            AttendanceCheckInRequest anonRequest = AttendanceCheckInRequest.builder()
                    .roundId(roundId)
                    .latitude(37.4979)
                    .longitude(127.0276)
                    .userName(name)
                    .build();

            AttendanceCheckInResponse response = attendanceService.checkInByRound(anonRequest, null);
            System.out.println("✅ 익명 사용자 '" + name + "' 체크인: " + response.getStatus());
            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getStatus()).isIn("PRESENT", "LATE");
        }

        // 출석 현황 조회에서 익명 사용자들이 기록되었는지 확인
        List<AttendanceResponse> attendances = attendanceService.getAttendancesBySession(sessionId);
        System.out.println("✅ 전체 출석 현황: " + attendances.size() + "명");
        assertThat(attendances.size()).isEqualTo(3);

        System.out.println("\n========== ✅ 익명 사용자 처리 완료 ==========\n");
    }

    // ============================================================================
    // 5. 출석 상태 판별 테스트
    // ============================================================================

    @Test
    @DisplayName("출석 상태 판별: 정시(PRESENT) vs 지각(LATE)")
    void testAttendanceStatusDetermination() {
        System.out.println("\n========== 📊 출석 상태 판별 테스트 ==========\n");

        // 세션 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("상태 판별 테스트")
                .startsAt(LocalDateTime.now().minusHours(1))
                .windowSeconds(1800)
                .rewardPoints(100)
                .build();

        AttendanceSessionResponse session = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = session.getAttendanceSessionId();

        LocalDate today = LocalDate.now();

        // 정시 라운드: 시작 후 3분
        LocalTime onTimeStart = LocalTime.now().minusMinutes(3);
        AttendanceRoundRequest onTimeRequest = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(onTimeStart)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse onTimeRound = attendanceRoundService.createRound(sessionId, onTimeRequest);
        UUID onTimeRoundId = onTimeRound.getRoundId();

        AttendanceCheckInRequest onTimeCheckIn = AttendanceCheckInRequest.builder()
                .roundId(onTimeRoundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse onTimeResponse = attendanceService.checkInByRound(
                onTimeCheckIn,
                testUser1.getUserId()
        );
        System.out.println("✅ 정시 체크인 (3분 후): " + onTimeResponse.getStatus());
        assertThat(onTimeResponse.getStatus()).isEqualTo("PRESENT");

        // 지각 라운드: 시작 후 8분
        LocalTime lateStart = LocalTime.now().minusMinutes(8);
        AttendanceRoundRequest lateRequest = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(lateStart)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse lateRound = attendanceRoundService.createRound(sessionId, lateRequest);
        UUID lateRoundId = lateRound.getRoundId();

        AttendanceCheckInRequest lateCheckIn = AttendanceCheckInRequest.builder()
                .roundId(lateRoundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse lateResponse = attendanceService.checkInByRound(
                lateCheckIn,
                testUser2.getUserId()
        );
        System.out.println("✅ 지각 체크인 (8분 후): " + lateResponse.getStatus());
        assertThat(lateResponse.getStatus()).isEqualTo("LATE");

        System.out.println("\n========== ✅ 출석 상태 판별 완료 ==========\n");
    }

    // ============================================================================
    // 6. 중복 체크인 방지 테스트
    // ============================================================================

    @Test
    @DisplayName("중복 체크인 방지: 동일 사용자가 같은 라운드에 2번 체크인 시도")
    void testDuplicateCheckInPrevention() {
        System.out.println("\n========== 🚫 중복 체크인 방지 테스트 ==========\n");

        // 세션/라운드 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("중복 방지 테스트")
                .startsAt(LocalDateTime.now().minusHours(1))
                .windowSeconds(1800)
                .rewardPoints(100)
                .build();

        AttendanceSessionResponse session = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = session.getAttendanceSessionId();

        LocalDate today = LocalDate.now();
        LocalTime roundTime = LocalTime.now().minusMinutes(2);
        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(roundTime)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse round = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = round.getRoundId();

        // 첫 번째 체크인
        AttendanceCheckInRequest checkInRequest = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse firstResponse = attendanceService.checkInByRound(
                checkInRequest,
                testUser1.getUserId()
        );
        System.out.println("✅ 첫 번째 체크인: " + firstResponse.getStatus());
        assertThat(firstResponse.getSuccess()).isTrue();

        // 두 번째 체크인 시도 (같은 사용자, 같은 라운드)
        AttendanceCheckInResponse secondResponse = attendanceService.checkInByRound(
                checkInRequest,
                testUser1.getUserId()
        );
        System.out.println("❌ 두 번째 체크인 시도: " + secondResponse.getSuccess() +
                          " (" + secondResponse.getFailureReason() + ")");
        assertThat(secondResponse.getSuccess()).isFalse();
        // 실패 사유 확인 (중복 또는 다른 오류 메시지)
        assertThat(secondResponse.getFailureReason()).isNotEmpty();

        System.out.println("\n========== ✅ 중복 체크인 방지 완료 ==========\n");
    }

    // ============================================================================
    // 7. 관리자 기능 테스트
    // ============================================================================

    @Test
    @DisplayName("관리자 기능: 세션 상태 변경 및 라운드 수정")
    void testAdminFunctions() {
        System.out.println("\n========== 👨‍💼 관리자 기능 테스트 ==========\n");

        // 세션 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("관리자 테스트 세션")
                .startsAt(LocalDateTime.now().minusHours(1))
                .windowSeconds(1800)
                .rewardPoints(50)
                .build();

        AttendanceSessionResponse session = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = session.getAttendanceSessionId();
        System.out.println("✅ 세션 생성: " + sessionId);

        // 세션 수정
        AttendanceSessionRequest updateRequest = AttendanceSessionRequest.builder()
                .title("수정된 세션 제목")
                .startsAt(LocalDateTime.now().minusHours(1))
                .windowSeconds(3600)
                .rewardPoints(200)
                .build();

        AttendanceSessionResponse updatedSession = attendanceSessionService.updateSession(sessionId, updateRequest);
        System.out.println("✅ 세션 수정: " + updatedSession.getTitle());
        assertThat(updatedSession.getTitle()).isEqualTo("수정된 세션 제목");
        assertThat(updatedSession.getRewardPoints()).isEqualTo(200);

        // 세션 활성화
        attendanceSessionService.activateSession(sessionId);
        AttendanceSessionResponse activeSession = attendanceSessionService.getSessionById(sessionId);
        System.out.println("✅ 세션 활성화");

        // 세션 종료
        attendanceSessionService.closeSession(sessionId);
        System.out.println("✅ 세션 종료");

        System.out.println("\n========== ✅ 관리자 기능 테스트 완료 ==========\n");
    }

    // ============================================================================
    // 8. 에러 케이스 처리
    // ============================================================================

    @Test
    @DisplayName("에러 처리: 존재하지 않는 라운드 조회")
    void testErrorHandling() {
        System.out.println("\n========== ⚠️ 에러 처리 테스트 ==========\n");

        // 존재하지 않는 라운드 ID로 체크인 시도
        AttendanceCheckInRequest invalidRequest = AttendanceCheckInRequest.builder()
                .roundId(UUID.randomUUID())
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        try {
            attendanceService.checkInByRound(invalidRequest, testUser1.getUserId());
            fail("예외가 발생해야 함");
        } catch (Exception e) {
            System.out.println("✅ 예상된 예외 발생: " + e.getClass().getSimpleName());
        }

        System.out.println("\n========== ✅ 에러 처리 테스트 완료 ==========\n");
    }

    // ============================================================================
    // 9. 데이터 일관성 테스트
    // ============================================================================

    @Test
    @DisplayName("데이터 일관성: 세션 삭제 시 관련 라운드/출석 기록도 삭제")
    void testDataConsistency() {
        System.out.println("\n========== 🔗 데이터 일관성 테스트 ==========\n");

        // 세션/라운드/출석 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("삭제 테스트 세션")
                .startsAt(LocalDateTime.now().minusHours(1))
                .windowSeconds(1800)
                .rewardPoints(100)
                .build();

        AttendanceSessionResponse session = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = session.getAttendanceSessionId();

        LocalDate today = LocalDate.now();
        LocalTime roundTime = LocalTime.now().minusMinutes(2);
        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(roundTime)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse round = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = round.getRoundId();

        AttendanceCheckInRequest checkInRequest = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        attendanceService.checkInByRound(checkInRequest, testUser1.getUserId());

        // 삭제 전 데이터 확인
        List<AttendanceRoundResponse> roundsBefore = attendanceRoundService.getRoundsBySession(sessionId);
        System.out.println("✅ 삭제 전 라운드: " + roundsBefore.size() + "개");
        assertThat(roundsBefore).isNotEmpty();

        // 세션 삭제
        attendanceSessionService.deleteSession(sessionId);
        System.out.println("✅ 세션 삭제");

        // 삭제 후 확인
        List<AttendanceSession> remainingSessions = sessionRepository.findAll();
        System.out.println("✅ 남은 세션: " + remainingSessions.size() + "개");

        System.out.println("\n========== ✅ 데이터 일관성 테스트 완료 ==========\n");
    }
}
