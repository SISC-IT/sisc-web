package org.sejongisc.backend.attendance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sejongisc.backend.attendance.dto.AttendanceCheckInRequest;
import org.sejongisc.backend.attendance.dto.AttendanceCheckInResponse;
import org.sejongisc.backend.attendance.dto.AttendanceRoundRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.service.AttendanceRoundService;
import org.sejongisc.backend.attendance.service.AttendanceService;
import org.sejongisc.backend.attendance.service.AttendanceSessionService;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * 라운드 출석 체크인 시간 검증 테스트
 * - 시간 범위 내 체크인 (성공)
 * - 시간 범위 초과 체크인 (실패)
 * - 지각 판별 검증
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@DisplayName("라운드 출석 체크인 시간 검증 테스트")
public class AttendanceCheckInTimeValidationTest {

    @Autowired
    private AttendanceSessionService attendanceSessionService;

    @Autowired
    private AttendanceRoundService attendanceRoundService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("✅ 성공: 라운드 시간 범위 내에 체크인")
    void checkInWithinTimeRange_success() {
        System.out.println("\n========== 🎯 시간 범위 내 체크인 테스트 시작 ==========\n");

        // 1️⃣ 세션 생성
        System.out.println("📝 Step 1: 세션 생성");
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("시간 검증 테스트 세션")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .rewardPoints(100)
                .build();

        var sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();
        System.out.println("  ✅ 세션 생성: " + sessionId);

        // 2️⃣ 라운드 생성 (중요: 오늘 날짜!)
        System.out.println("\n📝 Step 2: 라운드 생성 (⚠️ 반드시 오늘 날짜)");
        LocalTime now = LocalTime.now();
        LocalTime roundStartTime = now.minusMinutes(5);  // 5분 전에 시작
        LocalDate today = LocalDate.now();               // 오늘 날짜!

        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(today)                        // ✅ 오늘 날짜
                .startTime(roundStartTime)               // ✅ 지금보다 과거 시간
                .allowedMinutes(30)                      // 30분 동안 출석 가능
                .build();

        var roundResponse = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = roundResponse.getRoundId();

        System.out.println("  ✅ 라운드 생성 완료:");
        System.out.println("     - 라운드 날짜: " + roundResponse.getRoundDate());
        System.out.println("     - 시작 시간: " + roundResponse.getStartTime());
        System.out.println("     - 종료 시간: " + roundResponse.getStartTime().plusMinutes(30));
        System.out.println("     - 현재 시간: " + LocalTime.now());
        System.out.println("     - 상태: " + roundResponse.getStatus());

        // 3️⃣ 사용자 생성
        System.out.println("\n📝 Step 3: 사용자 생성");
        User student = User.builder()
                .email("checkin@test.com")
                .name("출석테스트학생")
                .role(Role.TEAM_MEMBER)
                .build();
        student = userRepository.save(student);
        System.out.println("  ✅ 사용자 생성: " + student.getName());

        // 4️⃣ 출석 체크인 (시간 범위 내)
        System.out.println("\n📝 Step 4: 출석 체크인 시작");
        AttendanceCheckInRequest checkInRequest = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        System.out.println("  📋 체크인 요청:");
        System.out.println("     - 라운드ID: " + roundId);
        System.out.println("     - 현재시간: " + LocalTime.now());
        System.out.println("     - 라운드 시작: " + roundResponse.getStartTime());
        System.out.println("     - 라운드 종료: " + roundResponse.getStartTime().plusMinutes(30));

        AttendanceCheckInResponse response = attendanceService.checkInByRound(checkInRequest, student.getUserId());

        // 5️⃣ 검증
        System.out.println("\n📝 Step 5: 검증");
        System.out.println("  ✅ 체크인 결과:");
        System.out.println("     - 성공: " + response.getSuccess());
        System.out.println("     - 상태: " + response.getStatus());
        System.out.println("     - 포인트: " + response.getAwardedPoints());
        System.out.println("     - 체크인 시간: " + response.getCheckedAt());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getStatus()).isIn("PRESENT", "LATE");
        assertThat(response.getAwardedPoints()).isEqualTo(100);

        System.out.println("\n========== ✅ 시간 범위 내 체크인 테스트 성공! ==========\n");
    }

    @Test
    @DisplayName("❌ 실패: 라운드 날짜 이후에 체크인 시도")
    void checkInAfterRoundDate_failure() {
        System.out.println("\n========== 🎯 라운드 날짜 이후 체크인 실패 테스트 시작 ==========\n");

        // 1️⃣ 세션 생성
        System.out.println("📝 Step 1: 세션 생성");
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("미래 라운드 테스트")
                .startsAt(LocalDateTime.now().plusDays(2))
                .windowSeconds(1800)
                .rewardPoints(100)
                .build();

        var sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();
        System.out.println("  ✅ 세션 생성: " + sessionId);

        // 2️⃣ 라운드 생성 (미래 날짜!)
        System.out.println("\n📝 Step 2: 라운드 생성 (⚠️ 내일 날짜)");
        LocalTime roundStartTime = LocalTime.of(10, 0);
        LocalDate tomorrow = LocalDate.now().plusDays(1);  // 내일 날짜

        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(tomorrow)                    // ❌ 내일 날짜
                .startTime(roundStartTime)
                .allowedMinutes(30)
                .build();

        var roundResponse = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = roundResponse.getRoundId();

        System.out.println("  ✅ 라운드 생성 완료:");
        System.out.println("     - 라운드 날짜: " + roundResponse.getRoundDate() + " (내일)");
        System.out.println("     - 현재 날짜: " + LocalDate.now());

        // 3️⃣ 사용자 생성
        System.out.println("\n📝 Step 3: 사용자 생성");
        User student = User.builder()
                .email("future@test.com")
                .name("미래테스트학생")
                .role(Role.TEAM_MEMBER)
                .build();
        student = userRepository.save(student);
        System.out.println("  ✅ 사용자 생성: " + student.getName());

        // 4️⃣ 출석 체크인 시도 (날짜가 맞지 않아서 실패)
        System.out.println("\n📝 Step 4: 출석 체크인 시도 (실패 예상)");
        AttendanceCheckInRequest checkInRequest = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse response = attendanceService.checkInByRound(checkInRequest, student.getUserId());

        // 5️⃣ 검증
        System.out.println("\n📝 Step 5: 검증");
        System.out.println("  ❌ 체크인 결과 (실패):");
        System.out.println("     - 성공: " + response.getSuccess());
        System.out.println("     - 실패 사유: " + response.getFailureReason());

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getFailureReason()).contains("출석 시간 초과");

        System.out.println("\n========== ✅ 라운드 날짜 이후 체크인 실패 테스트 성공! ==========\n");
    }

    @Test
    @DisplayName("❌ 실패: 라운드 시간 범위를 초과해서 체크인")
    void checkInAfterRoundTime_failure() {
        System.out.println("\n========== 🎯 라운드 시간 범위 초과 체크인 실패 테스트 시작 ==========\n");

        // 1️⃣ 세션 생성
        System.out.println("📝 Step 1: 세션 생성");
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("시간 초과 테스트")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .rewardPoints(100)
                .build();

        var sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();
        System.out.println("  ✅ 세션 생성: " + sessionId);

        // 2️⃣ 라운드 생성 (지난 시간!)
        System.out.println("\n📝 Step 2: 라운드 생성 (⚠️ 짧은 시간 범위)");
        LocalTime now = LocalTime.now();
        LocalTime roundStartTime = now.minusMinutes(40);  // 40분 전
        LocalDate today = LocalDate.now();

        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(roundStartTime)
                .allowedMinutes(30)                      // 30분만 허용 (이미 지남)
                .build();

        var roundResponse = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = roundResponse.getRoundId();

        System.out.println("  ✅ 라운드 생성 완료:");
        System.out.println("     - 라운드 시작: " + roundResponse.getStartTime());
        System.out.println("     - 라운드 종료: " + roundResponse.getStartTime().plusMinutes(30) + " (이미 지남)");
        System.out.println("     - 현재 시간: " + LocalTime.now());

        // 3️⃣ 사용자 생성
        System.out.println("\n📝 Step 3: 사용자 생성");
        User student = User.builder()
                .email("timeout@test.com")
                .name("시간초과테스트학생")
                .role(Role.TEAM_MEMBER)
                .build();
        student = userRepository.save(student);
        System.out.println("  ✅ 사용자 생성: " + student.getName());

        // 4️⃣ 출석 체크인 시도 (시간 초과)
        System.out.println("\n📝 Step 4: 출석 체크인 시도 (실패 예상)");
        AttendanceCheckInRequest checkInRequest = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse response = attendanceService.checkInByRound(checkInRequest, student.getUserId());

        // 5️⃣ 검증
        System.out.println("\n📝 Step 5: 검증");
        System.out.println("  ❌ 체크인 결과 (실패):");
        System.out.println("     - 성공: " + response.getSuccess());
        System.out.println("     - 실패 사유: " + response.getFailureReason());

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getFailureReason()).contains("출석 시간 초과");

        System.out.println("\n========== ✅ 시간 범위 초과 체크인 실패 테스트 성공! ==========\n");
    }

    @Test
    @DisplayName("📊 지각 판별 테스트")
    void lateCheckInDetection() {
        System.out.println("\n========== 🎯 지각 판별 테스트 시작 ==========\n");

        // 1️⃣ 세션 생성
        System.out.println("📝 Step 1: 세션 생성");
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("지각 판별 테스트")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .rewardPoints(100)
                .build();

        var sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();
        System.out.println("  ✅ 세션 생성: " + sessionId);

        // 2️⃣ 라운드 생성
        System.out.println("\n📝 Step 2: 라운드 생성");
        LocalTime now = LocalTime.now();
        LocalTime roundStartTime = now.minusMinutes(6);  // 6분 전 (지각 판별 기준: 5분)
        LocalDate today = LocalDate.now();

        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(today)
                .startTime(roundStartTime)
                .allowedMinutes(30)
                .build();

        var roundResponse = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = roundResponse.getRoundId();

        System.out.println("  ✅ 라운드 생성 완료:");
        System.out.println("     - 라운드 시작: " + roundResponse.getStartTime());
        System.out.println("     - 현재 시간: " + LocalTime.now());
        System.out.println("     - 시작 후 경과: 약 6분 (지각 기준: 5분)");

        // 3️⃣ 사용자 생성
        System.out.println("\n📝 Step 3: 사용자 생성");
        User student = User.builder()
                .email("late@test.com")
                .name("지각테스트학생")
                .role(Role.TEAM_MEMBER)
                .build();
        student = userRepository.save(student);
        System.out.println("  ✅ 사용자 생성: " + student.getName());

        // 4️⃣ 출석 체크인
        System.out.println("\n📝 Step 4: 출석 체크인");
        AttendanceCheckInRequest checkInRequest = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse response = attendanceService.checkInByRound(checkInRequest, student.getUserId());

        // 5️⃣ 검증
        System.out.println("\n📝 Step 5: 검증");
        System.out.println("  ✅ 체크인 결과:");
        System.out.println("     - 성공: " + response.getSuccess());
        System.out.println("     - 상태: " + response.getStatus());
        System.out.println("     - 예상: LATE (지각)");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("LATE");

        System.out.println("\n========== ✅ 지각 판별 테스트 성공! ==========\n");
    }
}
