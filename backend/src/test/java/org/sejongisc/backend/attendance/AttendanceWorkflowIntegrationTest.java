package org.sejongisc.backend.attendance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sejongisc.backend.attendance.dto.*;
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
 * Attendance 시스템 전체 워크플로우 통합 테스트
 *
 * 시나리오:
 * 1. 관리자가 세션 생성
 * 2. 관리자가 세션에 라운드 2개 생성
 * 3. 학생 2명이 라운드에 출석 체크인
 * 4. 출석 현황 조회 (라운드별, 세션별, 개인별)
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@DisplayName("Attendance 전체 워크플로우 통합 테스트")
public class AttendanceWorkflowIntegrationTest {

    @Autowired
    private AttendanceSessionService attendanceSessionService;

    @Autowired
    private AttendanceRoundService attendanceRoundService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("완전한 출석 관리 워크플로우 테스트")
    void completeAttendanceWorkflow() {
        System.out.println("\n========== 🎯 Attendance 전체 워크플로우 테스트 시작 ==========\n");

        // ===== Step 1: 사용자 생성 =====
        System.out.println("📝 Step 1: 테스트 사용자 생성");
        User student1 = User.builder()
                .email("student1@test.com")
                .name("김학생")
                .role(Role.TEAM_MEMBER)
                .build();
        student1 = userRepository.save(student1);
        System.out.println("  ✅ 학생 1 생성: ID=" + student1.getUserId() + ", 이름=" + student1.getName());

        User student2 = User.builder()
                .email("student2@test.com")
                .name("이학생")
                .role(Role.TEAM_MEMBER)
                .build();
        student2 = userRepository.save(student2);
        System.out.println("  ✅ 학생 2 생성: ID=" + student2.getUserId() + ", 이름=" + student2.getName());

        // ===== Step 2: 세션 생성 =====
        System.out.println("\n📝 Step 2: 출석 세션 생성");
        LocalDateTime sessionStart = LocalDateTime.now().plusHours(1);
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("금융 IT팀 정기모임")
                .startsAt(sessionStart)
                .windowSeconds(1800)  // 30분
                .rewardPoints(100)
                .build();

        AttendanceSessionResponse sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();
        System.out.println("  ✅ 세션 생성: ID=" + sessionId);
        System.out.println("     - 제목: " + sessionResponse.getTitle());
        System.out.println("     - 시작: " + sessionStart);
        System.out.println("     - 포인트: " + sessionResponse.getRewardPoints());

        // ===== Step 3: 라운드 생성 =====
        System.out.println("\n📝 Step 3: 라운드 생성");

        // 라운드 1: 현재 날짜, 최근 시간 (출석 가능 상태, 정시 판정을 위해 시작 3분 이내)
        LocalDate round1Date = LocalDate.now();
        LocalTime round1Time = LocalTime.now().minusMinutes(2);
        AttendanceRoundRequest round1Request = AttendanceRoundRequest.builder()
                .roundDate(round1Date)
                .startTime(round1Time)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse round1Response = attendanceRoundService.createRound(sessionId, round1Request);
        UUID roundId1 = round1Response.getRoundId();
        System.out.println("  ✅ 라운드 1 생성: ID=" + roundId1);
        System.out.println("     - 날짜: " + round1Response.getRoundDate());
        System.out.println("     - 시간: " + round1Response.getStartTime() + " ~ " +
                          round1Response.getStartTime().plusMinutes(round1Response.getAvailableMinutes()));
        System.out.println("     - 상태: " + round1Response.getStatus());

        // 라운드 2
        LocalDate round2Date = LocalDate.now().plusDays(1);
        LocalTime round2Time = LocalTime.of(14, 0);
        AttendanceRoundRequest round2Request = AttendanceRoundRequest.builder()
                .roundDate(round2Date)
                .startTime(round2Time)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse round2Response = attendanceRoundService.createRound(sessionId, round2Request);
        UUID roundId2 = round2Response.getRoundId();
        System.out.println("  ✅ 라운드 2 생성: ID=" + roundId2);
        System.out.println("     - 날짜: " + round2Response.getRoundDate());
        System.out.println("     - 상태: " + round2Response.getStatus());

        // ===== Step 4: 세션 상세 조회 =====
        System.out.println("\n📝 Step 4: 세션 상세 조회");
        AttendanceSessionResponse detailedSession = attendanceSessionService.getSessionById(sessionId);
        System.out.println("  ✅ 세션 조회 완료");
        System.out.println("     - ID: " + detailedSession.getAttendanceSessionId());
        System.out.println("     - 제목: " + detailedSession.getTitle());

        // ===== Step 5: 세션의 라운드 목록 조회 =====
        System.out.println("\n📝 Step 5: 세션의 라운드 목록 조회");
        List<AttendanceRoundResponse> roundList = attendanceRoundService.getRoundsBySession(sessionId);
        assertThat(roundList).hasSize(2);
        System.out.println("  ✅ 라운드 목록 조회: " + roundList.size() + "개");
        for (AttendanceRoundResponse round : roundList) {
            System.out.println("     - " + round.getRoundDate() + " " + round.getStartTime() +
                             " (상태: " + round.getStatus() + ")");
        }

        // ===== Step 6: 라운드 1에서 학생 1 출석 체크인 (정시) =====
        System.out.println("\n📝 Step 6: 학생 1 라운드 1 출석 체크인 (정시)");
        AttendanceCheckInRequest checkInRequest1 = AttendanceCheckInRequest.builder()
                .roundId(roundId1)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse checkInResponse1 = attendanceService.checkInByRound(
                checkInRequest1,
                student1.getUserId()
        );
        System.out.println("  ✅ 출석 체크인 완료");
        System.out.println("     - 사용자: " + student1.getName());
        System.out.println("     - 상태: " + checkInResponse1.getStatus());
        System.out.println("     - 성공: " + checkInResponse1.getSuccess());
        System.out.println("     - 포인트: " + checkInResponse1.getAwardedPoints());
        System.out.println("     - 체크인 시간: " + checkInResponse1.getCheckedAt());

        assertThat(checkInResponse1.getSuccess()).isTrue();
        assertThat(checkInResponse1.getStatus()).isEqualTo("PRESENT");
        assertThat(checkInResponse1.getAwardedPoints()).isEqualTo(100);

        // ===== Step 7: 라운드 1에서 학생 2 출석 체크인 (익명) =====
        System.out.println("\n📝 Step 7: 학생 2 라운드 1 출석 체크인 (익명)");
        AttendanceCheckInRequest checkInRequest2 = AttendanceCheckInRequest.builder()
                .roundId(roundId1)
                .latitude(37.4979)
                .longitude(127.0276)
                .userName("박익명")
                .build();

        AttendanceCheckInResponse checkInResponse2 = attendanceService.checkInByRound(
                checkInRequest2,
                student2.getUserId()
        );
        System.out.println("  ✅ 출석 체크인 완료");
        System.out.println("     - 사용자: " + student2.getName());
        System.out.println("     - 상태: " + checkInResponse2.getStatus());
        System.out.println("     - 성공: " + checkInResponse2.getSuccess());

        assertThat(checkInResponse2.getSuccess()).isTrue();

        // ===== Step 8: 라운드 1 출석 명단 조회 =====
        System.out.println("\n📝 Step 8: 라운드 1 출석 명단 조회");
        List<AttendanceResponse> roundAttendances = attendanceService.getAttendancesByRound(roundId1);
        System.out.println("  ✅ 출석 명단: " + roundAttendances.size() + "명");
        for (AttendanceResponse att : roundAttendances) {
            System.out.println("     - " + att.getUserName() + " (" + att.getAttendanceStatus() + ") " +
                             att.getCheckedAt() + " +포인트:" + att.getAwardedPoints());
        }

        assertThat(roundAttendances).hasSize(2);

        // ===== Step 9: 세션 출석 명단 조회 =====
        System.out.println("\n📝 Step 9: 세션 출석 명단 조회 (관리자용)");
        List<AttendanceResponse> sessionAttendances = attendanceService.getAttendancesBySession(sessionId);
        System.out.println("  ✅ 세션 출석 명단: " + sessionAttendances.size() + "명");
        for (AttendanceResponse att : sessionAttendances) {
            System.out.println("     - " + att.getUserName() + " (" + att.getAttendanceStatus() + ") " +
                             att.getCheckedAt());
        }

        assertThat(sessionAttendances).hasSize(2);

        // ===== Step 10: 개인별 출석 기록 조회 =====
        System.out.println("\n📝 Step 10: 학생 1 개인별 출석 기록 조회");
        List<AttendanceResponse> student1Attendances = attendanceService.getAttendancesByUser(student1.getUserId());
        System.out.println("  ✅ 출석 기록: " + student1Attendances.size() + "개");
        for (AttendanceResponse att : student1Attendances) {
            System.out.println("     - 세션: " + att.getAttendanceSessionId());
            System.out.println("       상태: " + att.getAttendanceStatus());
            System.out.println("       시간: " + att.getCheckedAt());
        }

        assertThat(student1Attendances).hasSize(1);
        assertThat(student1Attendances.get(0).getUserId()).isEqualTo(student1.getUserId());

        // ===== Step 11: 라운드 2 출석 체크인 실패 테스트 (시간 초과) =====
        System.out.println("\n📝 Step 11: 라운드 2 출석 체크인 실패 테스트 (시간 초과)");
        AttendanceCheckInRequest failRequest = AttendanceCheckInRequest.builder()
                .roundId(roundId2)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse failResponse = attendanceService.checkInByRound(
                failRequest,
                student1.getUserId()
        );
        System.out.println("  ✅ 체크인 실패 (예상된 결과)");
        System.out.println("     - 성공: " + failResponse.getSuccess());
        System.out.println("     - 실패 사유: " + failResponse.getFailureReason());

        assertThat(failResponse.getSuccess()).isFalse();
        assertThat(failResponse.getFailureReason()).contains("출석 시간");

        System.out.println("\n========== ✅ 모든 테스트 성공! ==========\n");
    }

    @Test
    @DisplayName("중복 출석 방지 테스트")
    void preventDuplicateAttendance() {
        System.out.println("\n========== 🎯 중복 출석 방지 테스트 ==========\n");

        // 세션 및 라운드 생성
        User student = User.builder()
                .email("duplicate@test.com")
                .name("중복테스트학생")
                .role(Role.TEAM_MEMBER)
                .build();
        student = userRepository.save(student);

        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("중복 방지 테스트")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .rewardPoints(50)
                .build();

        AttendanceSessionResponse sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();

        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(LocalDate.now())
                .startTime(LocalTime.now().minusMinutes(2))
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse roundResponse = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = roundResponse.getRoundId();

        // 첫 번째 체크인 (성공)
        System.out.println("📝 첫 번째 체크인 시도");
        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse response1 = attendanceService.checkInByRound(request, student.getUserId());
        System.out.println("  ✅ 첫 번째 체크인 성공");
        System.out.println("     - 상태: " + response1.getStatus());

        assertThat(response1.getSuccess()).isTrue();

        // 두 번째 체크인 (실패 - 중복)
        System.out.println("\n📝 두 번째 체크인 시도 (중복)");
        AttendanceCheckInResponse response2 = attendanceService.checkInByRound(request, student.getUserId());
        System.out.println("  ✅ 중복 출석 방지됨");
        System.out.println("     - 성공: " + response2.getSuccess());
        System.out.println("     - 실패 사유: " + response2.getFailureReason());

        assertThat(response2.getSuccess()).isFalse();
        assertThat(response2.getFailureReason()).contains("이미");

        System.out.println("\n========== ✅ 중복 방지 테스트 성공! ==========\n");
    }

    @Test
    @DisplayName("익명 사용자 출석 처리 테스트")
    void anonymousUserAttendance() {
        System.out.println("\n========== 🎯 익명 사용자 출석 처리 테스트 ==========\n");

        // 세션 및 라운드 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("익명사용자 테스트")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .rewardPoints(50)
                .build();

        AttendanceSessionResponse sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();

        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(LocalDate.now())
                .startTime(LocalTime.now().minusMinutes(2))
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse roundResponse = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = roundResponse.getRoundId();

        // 익명 사용자 1 (이름 입력)
        System.out.println("📝 익명 사용자 1 체크인 (이름 입력)");
        AttendanceCheckInRequest anonRequest1 = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .userName("익명사용자1")
                .build();

        AttendanceCheckInResponse anonResponse1 = attendanceService.checkInByRound(anonRequest1, null);
        System.out.println("  ✅ 익명 사용자 1 체크인 성공");
        System.out.println("     - 상태: " + anonResponse1.getStatus());
        System.out.println("     - 성공: " + anonResponse1.getSuccess());

        assertThat(anonResponse1.getSuccess()).isTrue();

        // 익명 사용자 2 (이름 미입력 - 자동생성)
        System.out.println("\n📝 익명 사용자 2 체크인 (이름 미입력)");
        AttendanceCheckInRequest anonRequest2 = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        AttendanceCheckInResponse anonResponse2 = attendanceService.checkInByRound(anonRequest2, null);
        System.out.println("  ✅ 익명 사용자 2 체크인 성공 (이름 자동생성)");
        System.out.println("     - 상태: " + anonResponse2.getStatus());

        assertThat(anonResponse2.getSuccess()).isTrue();

        // 라운드 출석 명단 확인
        System.out.println("\n📝 라운드 출석 명단 확인");
        List<AttendanceResponse> attendances = attendanceService.getAttendancesByRound(roundId);
        System.out.println("  ✅ 출석 명단: " + attendances.size() + "명");
        for (AttendanceResponse att : attendances) {
            System.out.println("     - " + att.getUserName() + " (익명: " + (att.getUserId() == null) + ")");
        }

        assertThat(attendances).hasSize(2);

        System.out.println("\n========== ✅ 익명 사용자 테스트 성공! ==========\n");
    }
}
