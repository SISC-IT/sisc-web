package org.sejongisc.backend.attendance.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.Location;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.entity.SessionVisibility;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AttendanceSessionServiceTest {

    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;
    @Mock
    private AttendanceRepository attendanceRepository;

    @InjectMocks
    private AttendanceSessionService attendanceSessionService;

    @Test
    @DisplayName("출석 세션 생성 성공: 위치 정보 포함")
    void createSession_success_withLocation() {
        //given
        AttendanceSessionRequest request = AttendanceSessionRequest.builder()
                .title("세투연 정기모임")
                .tag("금융IT")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .rewardPoints(10)
                .latitude(37.5665)
                .longitude(126.9780)
                .radiusMeters(100)
                .visibility(SessionVisibility.PUBLIC)
                .build();

        AttendanceSession savedSession = AttendanceSession.builder()
                .attendanceSessionId(UUID.randomUUID())
                .title("세투연 정기모임")
                .tag("금융IT")
                .startsAt(request.getStartsAt())
                .windowSeconds(1800)
                .code("123456")
                .rewardPoints(10)
                .location(Location.builder()
                        .lat(37.5665)
                        .lng(126.9780)
                        .radiusMeters(100)
                        .build())
                .visibility(SessionVisibility.PUBLIC)
                .status(SessionStatus.UPCOMING)
                .build();

        when(attendanceSessionRepository.existsByCode(anyString())).thenReturn(false);
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenReturn(savedSession);
        when(attendanceRepository.countByAttendanceSession(any(AttendanceSession.class))).thenReturn(0L);

        //when
        AttendanceSessionResponse response = attendanceSessionService.createSession(request);

        //then
        assertAll(
                () -> assertThat(response.getTitle()).isEqualTo("세투연 정기모임"),
                () -> assertThat(response.getRewardPoints()).isEqualTo(10),
                () -> assertThat(response.getLatitude()).isEqualTo(37.5665),
                () -> assertThat(response.getLongitude()).isEqualTo(126.9780),
                () -> assertThat(response.getRadiusMeters()).isEqualTo(100),
                () -> assertThat(response.getVisibility()).isEqualTo(SessionVisibility.PUBLIC),
                () -> assertThat(response.getStatus()).isEqualTo(SessionStatus.UPCOMING),
                () -> assertThat(response.getParticipantCount()).isEqualTo(0)
        );

        verify(attendanceSessionRepository).save(any(AttendanceSession.class));
    }

    @Test
    @DisplayName("출석 세션 생성 성공: 위치 정보 없음")
    void createSession_success_withoutLocation() {
        //given
        AttendanceSessionRequest request = AttendanceSessionRequest.builder()
                .title("정규세션")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(3600)
                .rewardPoints(10)
                .build();

        AttendanceSession savedSession = AttendanceSession.builder()
                .attendanceSessionId(UUID.randomUUID())
                .title("정규세션")
                .startsAt(request.getStartsAt())
                .windowSeconds(3600)
                .code("123456")
                .rewardPoints(10)
                .location(null)
                .visibility(SessionVisibility.PUBLIC)
                .status(SessionStatus.UPCOMING)
                .build();

        when(attendanceSessionRepository.existsByCode(anyString())).thenReturn(false);
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenReturn(savedSession);
        when(attendanceRepository.countByAttendanceSession(any(AttendanceSession.class))).thenReturn(0L);

        //when
        AttendanceSessionResponse response = attendanceSessionService.createSession(request);

        //then
        assertAll(
                () -> assertThat(response.getTitle()).isEqualTo("정규세션"),
                () -> assertThat(response.getLatitude()).isNull(),
                () -> assertThat(response.getLongitude()).isNull(),
                () -> assertThat(response.getRadiusMeters()).isNull(),
                () -> assertThat(response.getVisibility()).isEqualTo(SessionVisibility.PUBLIC)
        );
    }

    @Test
    @DisplayName("공개 세션 목록 조회")
    void getPublicSession_success() {
        //given
        List<AttendanceSession> sessions = Arrays.asList(
                AttendanceSession.builder()
                        .attendanceSessionId(UUID.randomUUID())
                        .title("정규세션")
                        .code("111111")
                        .startsAt(LocalDateTime.now().plusHours(1))
                        .windowSeconds(1800)
                        .visibility(SessionVisibility.PUBLIC)
                        .build(),
                AttendanceSession.builder()
                        .attendanceSessionId(UUID.randomUUID())
                        .title("정규세션2")
                        .code("222222")
                        .startsAt(LocalDateTime.now().plusHours(2))
                        .windowSeconds(1800)
                        .visibility(SessionVisibility.PUBLIC)
                        .build()

        );

        when(attendanceSessionRepository.findByVisibilityOrderByStartsAtDesc(SessionVisibility.PUBLIC)).thenReturn(sessions);
        when(attendanceRepository.countByAttendanceSession(any(AttendanceSession.class))).thenReturn(0L);

        //when
        List<AttendanceSessionResponse> responses = attendanceSessionService.getPublicSessions();

        //then
        assertAll(
                () -> assertThat(responses).hasSize(2),
                () -> assertThat(responses.get(0).getTitle()).isEqualTo("정규세션"),
                () -> assertThat(responses.get(1).getTitle()).isEqualTo("정규세션2")
        );
    }

    @Test
    @DisplayName("활성 세션 목록 조회")
    void getActiveSessions_success() {
        //given
        LocalDateTime now = LocalDateTime.now();
        List<AttendanceSession> allSessions = Arrays.asList(
                // 현재 활성 세션 (시작됨, 아직 끝나지 않음)
                AttendanceSession.builder()
                        .attendanceSessionId(UUID.randomUUID())
                        .title("활성세션")
                        .code("111111")
                        .startsAt(now.minusMinutes(10))
                        .windowSeconds(1800)
                        .status(SessionStatus.OPEN)
                        .build(),
                AttendanceSession.builder()
                        .attendanceSessionId(UUID.randomUUID())
                        .title("예정세션")
                        .code("222222")
                        .startsAt(now.plusMinutes(10))
                        .windowSeconds(1800)
                        .build(),
                AttendanceSession.builder()
                        .attendanceSessionId(UUID.randomUUID())
                        .title("종료세션")
                        .code("333333")
                        .startsAt(now.minusHours(2))
                        .windowSeconds(1800)
                        .build()
        );

        when(attendanceSessionRepository.findAllByOrderByStartsAtDesc()).thenReturn(allSessions);
        when(attendanceRepository.countByAttendanceSession(any(AttendanceSession.class))).thenReturn(0L);

        //when
        List<AttendanceSessionResponse> responses = attendanceSessionService.getActiveSessions();

        //then
        assertAll(
                () -> assertThat(responses).hasSize(1),
                () -> assertThat(responses.get(0).getTitle()).isEqualTo("활성세션"),
                () -> assertThat(responses.get(0).isCheckInAvailable()).isTrue()
        );
    }

    @Test
    @DisplayName("세션 수정 성공")
    void updateSession_success() {
        //given
        UUID sessionId = UUID.randomUUID();
        AttendanceSessionRequest request = AttendanceSessionRequest.builder()
                .title("수정된 제목")
                .tag("수정된 태그")
                .startsAt(LocalDateTime.now().plusHours(2))
                .windowSeconds(3600)
                .rewardPoints(10)
                .latitude(37.5000)
                .longitude(127.0000)
                .radiusMeters(200)
                .visibility(SessionVisibility.PRIVATE)
                .build();

        AttendanceSession existingSession = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("기존 제목")
                .code("123456")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .build();

        AttendanceSession updatedSession = existingSession.toBuilder()
                .title(request.getTitle())
                .tag(request.getTag())
                .startsAt(request.getStartsAt())
                .windowSeconds(request.getWindowSeconds())
                .rewardPoints(request.getRewardPoints())
                .location(Location.builder()
                        .lat(request.getLatitude())
                        .lng(request.getLongitude())
                        .radiusMeters(request.getRadiusMeters())
                        .build())
                .visibility(request.getVisibility())
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession));
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenReturn(updatedSession);
        when(attendanceRepository.countByAttendanceSession(any(AttendanceSession.class))).thenReturn(0L);

        //when
        AttendanceSessionResponse response = attendanceSessionService.updateSession(sessionId, request);

        //then
        assertAll(
                () -> assertThat(response.getTitle()).isEqualTo("수정된 제목"),
                () -> assertThat(response.getRewardPoints()).isEqualTo(10),
                () -> assertThat(response.getLatitude()).isEqualTo(37.5000),
                () -> assertThat(response.getLongitude()).isEqualTo(127.0000),
                () -> assertThat(response.getRadiusMeters()).isEqualTo(200),
                () -> assertThat(response.getVisibility()).isEqualTo(SessionVisibility.PRIVATE)
        );

        verify(attendanceSessionRepository).save(any(AttendanceSession.class));

    }

    @Test
    @DisplayName("세션 활성화 성공")
    void activateSession_success() {
        //given
        UUID sessionId = UUID.randomUUID();
        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("세투연 정규 세션")
                .status(SessionStatus.UPCOMING)
                .build();

        AttendanceSession activateSession = session.toBuilder()
                .status(SessionStatus.OPEN)
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenReturn(activateSession);

        //when
        attendanceSessionService.activateSession(sessionId);

        //then
        verify(attendanceSessionRepository).save(any(AttendanceSession.class));
    }

    @Test
    @DisplayName("세션 종료 성공")
    void closeSession_success() {
        //given
        UUID sessionId = UUID.randomUUID();
        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("세투연 정규 세션")
                .status(SessionStatus.OPEN)
                .build();

        AttendanceSession closedSession = session.toBuilder()
                .status(SessionStatus.CLOSED)
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenReturn(closedSession);

        //when
        attendanceSessionService.closeSession(sessionId);

        //then
        verify(attendanceSessionRepository).save(any(AttendanceSession.class));
    }

    @Test
    @DisplayName("세션 삭제 성공")
    void deleteSession_success() {
        //given
        UUID sessionId = UUID.randomUUID();
        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("세투연 정규 세션")
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        //when
        attendanceSessionService.deleteSession(sessionId);

        //then
        verify(attendanceSessionRepository).delete(session);
    }

}

