package org.sejongisc.backend.attendance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sejongisc.backend.attendance.dto.AttendanceRoundRequest;
import org.sejongisc.backend.attendance.dto.AttendanceRoundResponse;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.SessionVisibility;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * 세션과 라운드의 양방향 관계 검증 통합 테스트
 * - 세션 생성 후 라운드 생성
 * - 라운드가 세션에 올바르게 속하는지 확인
 * - 데이터베이스 레벨에서 FK 관계 검증
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class AttendanceSessionRoundIntegrationTest {

    @Autowired
    private AttendanceSessionService attendanceSessionService;

    @Autowired
    private AttendanceRoundService attendanceRoundService;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AttendanceRoundRepository attendanceRoundRepository;

    @Test
    @DisplayName("세션 생성 -> 라운드 생성 -> 라운드 조회 통합 테스트")
    void createSessionAndRoundIntegration_success() {
        // Given: 세션 생성 요청
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("금융 IT팀 정기모임")
                .tag("금융IT")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)  // 30분
                .rewardPoints(100)
                .visibility(SessionVisibility.PUBLIC)
                .build();

        // When 1: 세션 생성
        AttendanceSessionResponse sessionResponse = attendanceSessionService.createSession(sessionRequest);

        assertThat(sessionResponse).isNotNull();
        assertThat(sessionResponse.getAttendanceSessionId()).isNotNull();
        UUID sessionId = sessionResponse.getAttendanceSessionId();
        System.out.println("✅ 세션 생성 완료: sessionId=" + sessionId);

        // When 2: 라운드 생성 요청
        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(14, 0))
                .allowedMinutes(30)
                .build();

        // When 3: 라운드 생성
        AttendanceRoundResponse roundResponse = attendanceRoundService.createRound(sessionId, roundRequest);

        assertThat(roundResponse).isNotNull();
        assertThat(roundResponse.getRoundId()).isNotNull();
        UUID roundId = roundResponse.getRoundId();
        System.out.println("✅ 라운드 생성 완료: roundId=" + roundId);

        // Then 1: 라운드가 세션에 속하는지 확인 (FK 레벨)
        AttendanceRound roundFromDb = attendanceRoundRepository.findById(roundId)
                .orElseThrow(() -> new AssertionError("라운드를 찾을 수 없습니다"));

        assertThat(roundFromDb.getAttendanceSession()).isNotNull();
        assertThat(roundFromDb.getAttendanceSession().getAttendanceSessionId()).isEqualTo(sessionId);
        System.out.println("✅ 라운드의 세션 FK 확인 완료: roundId=" + roundId + ", sessionId=" + sessionId);

        // Then 2: 세션의 라운드 컬렉션에 포함되는지 확인 (양방향 관계)
        AttendanceSession sessionFromDb = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AssertionError("세션을 찾을 수 없습니다"));

        assertThat(sessionFromDb.getRounds()).isNotEmpty();
        assertThat(sessionFromDb.getRounds()).hasSize(1);
        assertThat(sessionFromDb.getRounds().get(0).getRoundId()).isEqualTo(roundId);
        System.out.println("✅ 세션의 라운드 컬렉션 확인 완료: sessionId=" + sessionId + ", roundCount=" + sessionFromDb.getRounds().size());

        // Then 3: getRoundsBySession() API로 조회 확인
        List<AttendanceRoundResponse> roundsBySession = attendanceRoundService.getRoundsBySession(sessionId);

        assertThat(roundsBySession).isNotEmpty();
        assertThat(roundsBySession).hasSize(1);
        assertThat(roundsBySession.get(0).getRoundId()).isEqualTo(roundId);
        assertThat(roundsBySession.get(0).getRoundDate()).isEqualTo(roundRequest.getRoundDate());
        assertThat(roundsBySession.get(0).getStartTime()).isEqualTo(roundRequest.getStartTime());
        System.out.println("✅ getRoundsBySession() 조회 완료: roundCount=" + roundsBySession.size());

        System.out.println("\n✅ 통합 테스트 성공: 라운드가 세션에 올바르게 속함");
    }

    @Test
    @DisplayName("동일 세션에 여러 라운드 생성 테스트")
    void createMultipleRoundsForSingleSession_success() {
        // Given: 세션 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("동아리 정기모임")
                .tag("동아리")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .rewardPoints(50)
                .visibility(SessionVisibility.PUBLIC)
                .build();

        AttendanceSessionResponse sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();
        System.out.println("✅ 세션 생성 완료: sessionId=" + sessionId);

        // When: 3개의 라운드 생성
        UUID roundId1 = null, roundId2 = null, roundId3 = null;

        for (int i = 1; i <= 3; i++) {
            AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                    .roundDate(LocalDate.now().plusDays(i))
                    .startTime(LocalTime.of(14, 0))
                    .allowedMinutes(30)
                    .build();

            AttendanceRoundResponse roundResponse = attendanceRoundService.createRound(sessionId, roundRequest);
            UUID roundId = roundResponse.getRoundId();

            if (i == 1) roundId1 = roundId;
            if (i == 2) roundId2 = roundId;
            if (i == 3) roundId3 = roundId;

            System.out.println("✅ 라운드 " + i + " 생성 완료: roundId=" + roundId);
        }

        // Then 1: 세션의 라운드 컬렉션 확인
        AttendanceSession sessionFromDb = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AssertionError("세션을 찾을 수 없습니다"));

        assertThat(sessionFromDb.getRounds()).hasSize(3);
        System.out.println("✅ 세션의 라운드 컬렉션 확인: roundCount=" + sessionFromDb.getRounds().size());

        // Then 2: getRoundsBySession() API로 조회 확인
        List<AttendanceRoundResponse> roundsBySession = attendanceRoundService.getRoundsBySession(sessionId);

        assertThat(roundsBySession).hasSize(3);
        assertThat(roundsBySession.get(0).getRoundId()).isEqualTo(roundId1);
        assertThat(roundsBySession.get(1).getRoundId()).isEqualTo(roundId2);
        assertThat(roundsBySession.get(2).getRoundId()).isEqualTo(roundId3);
        System.out.println("✅ getRoundsBySession() 조회 완료: 모든 라운드가 올바르게 조회됨");

        // Then 3: 각 라운드의 FK 확인
        for (int i = 0; i < 3; i++) {
            AttendanceRound round = attendanceRoundRepository.findById(roundsBySession.get(i).getRoundId())
                    .orElseThrow(() -> new AssertionError("라운드를 찾을 수 없습니다"));

            assertThat(round.getAttendanceSession().getAttendanceSessionId()).isEqualTo(sessionId);
        }
        System.out.println("✅ 모든 라운드의 FK 확인 완료");

        System.out.println("\n✅ 통합 테스트 성공: 여러 라운드가 세션에 올바르게 속함");
    }

    @Test
    @DisplayName("라운드 생성 전후 세션 조회 테스트")
    void querySessionBeforeAndAfterRoundCreation_success() {
        // Given: 세션 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("테스트 세션")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .visibility(SessionVisibility.PUBLIC)
                .build();

        AttendanceSessionResponse sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();

        // When 1: 라운드 생성 전 세션 조회
        AttendanceSession sessionBeforeRound = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AssertionError("세션을 찾을 수 없습니다"));

        assertThat(sessionBeforeRound.getRounds()).isEmpty();
        System.out.println("✅ 라운드 생성 전: 세션의 라운드 컬렉션이 비어있음");

        // When 2: 라운드 생성
        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(14, 0))
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse roundResponse = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = roundResponse.getRoundId();
        System.out.println("✅ 라운드 생성 완료: roundId=" + roundId);

        // When 3: 라운드 생성 후 세션 조회 (새로운 트랜잭션)
        AttendanceSession sessionAfterRound = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AssertionError("세션을 찾을 수 없습니다"));

        assertThat(sessionAfterRound.getRounds()).isNotEmpty();
        assertThat(sessionAfterRound.getRounds()).hasSize(1);
        assertThat(sessionAfterRound.getRounds().get(0).getRoundId()).isEqualTo(roundId);
        System.out.println("✅ 라운드 생성 후: 세션의 라운드 컬렉션에 라운드가 포함됨");

        System.out.println("\n✅ 통합 테스트 성공: 라운드 생성 전후 세션 상태가 올바르게 반영됨");
    }

    @Test
    @DisplayName("라운드 삭제 후 세션 관계 확인")
    void deleteRoundAndVerifySessionRelationship_success() {
        // Given: 세션과 라운드 생성
        AttendanceSessionRequest sessionRequest = AttendanceSessionRequest.builder()
                .title("삭제 테스트 세션")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .visibility(SessionVisibility.PUBLIC)
                .build();

        AttendanceSessionResponse sessionResponse = attendanceSessionService.createSession(sessionRequest);
        UUID sessionId = sessionResponse.getAttendanceSessionId();

        AttendanceRoundRequest roundRequest = AttendanceRoundRequest.builder()
                .roundDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(14, 0))
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse roundResponse = attendanceRoundService.createRound(sessionId, roundRequest);
        UUID roundId = roundResponse.getRoundId();

        // When 1: 라운드 삭제 전 확인
        AttendanceSession sessionBeforeDelete = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AssertionError("세션을 찾을 수 없습니다"));

        assertThat(sessionBeforeDelete.getRounds()).hasSize(1);
        System.out.println("✅ 삭제 전: 세션에 라운드 1개 포함");

        // When 2: 라운드 삭제
        attendanceRoundService.deleteRound(roundId);
        System.out.println("✅ 라운드 삭제 완료: roundId=" + roundId);

        // Then 1: 라운드가 DB에서 삭제되었는지 확인
        assertThat(attendanceRoundRepository.findById(roundId)).isEmpty();
        System.out.println("✅ 라운드가 DB에서 삭제됨");

        // Then 2: 세션의 라운드 컬렉션에서 제거되었는지 확인
        AttendanceSession sessionAfterDelete = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AssertionError("세션을 찾을 수 없습니다"));

        assertThat(sessionAfterDelete.getRounds()).isEmpty();
        System.out.println("✅ 삭제 후: 세션의 라운드 컬렉션이 비어있음");

        System.out.println("\n✅ 통합 테스트 성공: 라운드 삭제 후 관계가 올바르게 유지됨");
    }
}
