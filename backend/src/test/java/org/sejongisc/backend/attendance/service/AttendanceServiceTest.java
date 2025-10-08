package org.sejongisc.backend.attendance.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.attendance.dto.AttendanceRequest;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.entity.*;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    void 체크인_성공() {
        //given
        String code = "123456";
        AttendanceRequest request = AttendanceRequest.builder()
                .code(code)
                .latitude(37.5665)
                .longitude(126.9780)
                .note("정상 출석")
                .deviceInfo("IPhone 14")
                .build();

        User user = User.builder()
                .userId(UUID.randomUUID())
                .name("오찬혁")
                .email("oh@naver.com")
                .role(Role.TEAM_MEMBER)
                .build();

        Location sessionLocation = Location.builder()
                .lat(37.5665)
                .lng(126.9780)
                .radiusMeters(100)
                .build();

        LocalDateTime now = LocalDateTime.now();
        UUID sessionId = UUID.randomUUID();
        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("세투연 정기모임")
                .code(code)
                .startsAt(now.minusMinutes(5))
                .windowSeconds(1800)
                .rewardPoints(10)
                .location(sessionLocation)
                .status(SessionStatus.OPEN)
                .visibility(SessionVisibility.PUBLIC)
                .build();

        Attendance savedAttendance = Attendance.builder()
                .attendanceId(UUID.randomUUID())
                .user(user)
                .attendanceSession(session)
                .attendanceStatus(AttendanceStatus.LATE)
                .checkedAt(now)
                .awardedPoints(10)
                .note("정상 출석")
                .deviceInfo("IPhone 14")
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(attendanceRepository.existsByAttendanceSessionAndUser(session, user)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenReturn(savedAttendance);

        //when
        AttendanceResponse response = attendanceService.checkIn(sessionId, request, user);

        //then
        assertAll(
                () -> assertThat(response.getAttendanceStatus()).isEqualTo(AttendanceStatus.LATE),
                () -> assertThat(response.getUserName()).isEqualTo("오찬혁"),
                () -> assertThat(response.getAwardedPoints()).isEqualTo(10),
                () -> assertThat(response.getNote()).isEqualTo("정상 출석"),
                () -> assertThat(response.getDeviceInfo()).isEqualTo("IPhone 14")
        );

        verify(attendanceRepository).save(any(Attendance.class));
    }

    @Test
    void 체크인_실패_존재하지_않는_코드() {
        //given
        String invalidCode = "999999";
        AttendanceRequest request = AttendanceRequest.builder()
                .code(invalidCode)
                .latitude(37.5665)
                .longitude(126.9780)
                .build();

        User user = User.builder()
                .userId(UUID.randomUUID())
                .name("오찬혁")
                .build();

        UUID sessionId = UUID.randomUUID();
        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> attendanceService.checkIn(sessionId, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 세션입니다: " + sessionId);

        // attendanceRepository.save() 메서드가 호출되지 않았는지 검증
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 체크인_실패_중복출석() {
        //given
        String code = "123456";
        AttendanceRequest request = AttendanceRequest.builder()
                .code(code)
                .latitude(37.5665)
                .longitude(126.9780)
                .build();

        User user = User.builder()
                .userId(UUID.randomUUID())
                .name("오찬혁")
                .build();

        UUID sessionId = UUID.randomUUID();
        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .code(code)
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(attendanceRepository.existsByAttendanceSessionAndUser(session, user)).thenReturn(true);

        //then
        assertThatThrownBy(() -> attendanceService.checkIn(sessionId, request, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 출석 체크인한 세션입니다");

        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 체크인_실패_위치범위초과() {
        //given
        String code = "123456";
        AttendanceRequest request = AttendanceRequest.builder()
                .code(code)
                .latitude(37.6665)
                .longitude(127.0780)
                .build();

        User user = User.builder()
                .userId(UUID.randomUUID())
                .name("오찬혁")
                .build();

        Location sessionLocation = Location.builder()
                .lat(37.5665)
                .lng(126.9780)
                .radiusMeters(100)
                .build();

        UUID sessionId = UUID.randomUUID();
        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .code(code)
                .location(sessionLocation)
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(attendanceRepository.existsByAttendanceSessionAndUser(session, user)).thenReturn(false);

        //then
        assertThatThrownBy(() -> attendanceService.checkIn(sessionId, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출석 허용 범위를 벗어났습니다");

        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 세션별_출석_목록_조회() {
        //given
        UUID sessionId = UUID.randomUUID();
        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("세투연 정기모임")
                .build();

        User user1 = User.builder().userId(UUID.randomUUID()).name("오찬혁").build();
        User user2 = User.builder().userId(UUID.randomUUID()).name("김찬혁").build();

        List<Attendance> attendances = Arrays.asList(
                Attendance.builder()
                        .attendanceId(UUID.randomUUID())
                        .user(user1)
                        .attendanceSession(session)
                        .attendanceStatus(AttendanceStatus.PRESENT)
                        .checkedAt(LocalDateTime.now().minusHours(1))
                        .build(),
                Attendance.builder()
                        .attendanceId(UUID.randomUUID())
                        .user(user2)
                        .attendanceSession(session)
                        .attendanceStatus(AttendanceStatus.LATE)
                        .checkedAt(LocalDateTime.now())
                        .build()
        );

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(attendanceRepository.findByAttendanceSessionOrderByCheckedAtAsc(session)).thenReturn(attendances);

        //when
        List<AttendanceResponse> response = attendanceService.getAttendanceBySession(sessionId);

        //then
        assertAll(
                () -> assertThat(response).hasSize(2),
                () -> assertThat(response.get(0).getUserName()).isEqualTo("오찬혁"),
                () -> assertThat(response.get(0).getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT),
                () -> assertThat(response.get(1).getUserName()).isEqualTo("김찬혁"),
                () -> assertThat(response.get(1).getAttendanceStatus()).isEqualTo(AttendanceStatus.LATE)
        );
    }

    @Test
    void 출석_상태_수정성공() {
        //given
        UUID sessionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String newStatus = "PRESENT";
        String reason = "관리자 수정";

        User adminUser = User.builder()
                .userId(UUID.randomUUID())
                .name("관리자")
                .role(Role.PRESIDENT)
                .build();

        User student = User.builder()
                .userId(memberId)
                .name("오찬혁")
                .build();

        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("세투연 정기모임")
                .build();

        Attendance attendance = Attendance.builder()
                .attendanceId(UUID.randomUUID())
                .user(student)
                .attendanceSession(session)
                .attendanceStatus(AttendanceStatus.LATE)
                .checkedAt(LocalDateTime.now())
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(attendanceRepository.findByAttendanceSessionAndUser_UserId(session, memberId)).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(any(Attendance.class))).thenReturn(attendance);

        //when
        AttendanceResponse response = attendanceService.updateAttendanceStatus(
                sessionId, memberId, newStatus, reason, adminUser);

        //then
        assertAll(
                () -> assertThat(response.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT),
                () -> assertThat(response.getNote()).isEqualTo(reason)
        );

        verify(attendanceRepository).save(attendance);
    }

    @Test
    void 사용자별_출석_이력_조회() {
        //given
        User user = User.builder()
                .userId(UUID.randomUUID())
                .name("오찬혁")
                .build();

        AttendanceSession session1 = AttendanceSession.builder()
                .attendanceSessionId(UUID.randomUUID())
                .title("세션1")
                .build();

        AttendanceSession session2 = AttendanceSession.builder()
                .attendanceSessionId(UUID.randomUUID())
                .title("세션2")
                .build();

        List<Attendance> attendances = Arrays.asList(
                Attendance.builder()
                        .attendanceId(UUID.randomUUID())
                        .user(user)
                        .attendanceSession(session1)
                        .attendanceStatus(AttendanceStatus.PRESENT)
                        .checkedAt(LocalDateTime.now().minusDays(1))
                        .build(),
                Attendance.builder()
                        .attendanceId(UUID.randomUUID())
                        .user(user)
                        .attendanceSession(session2)
                        .attendanceStatus(AttendanceStatus.LATE)
                        .checkedAt(LocalDateTime.now())
                        .build()
        );

        when(attendanceRepository.findByUserOrderByCheckedAtDesc(user)).thenReturn(attendances);

        //when
        List<AttendanceResponse> responses = attendanceService.getAttendancesByUser(user);

        //then
        assertAll(
                () -> assertThat(responses).hasSize(2),
                () -> assertThat(responses.get(0).getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT),
                () -> assertThat(responses.get(1).getAttendanceStatus()).isEqualTo(AttendanceStatus.LATE)
        );

    }
}
