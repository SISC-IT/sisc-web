package org.sejongisc.backend.attendance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.attendance.dto.AttendanceCheckInRequest;
import org.sejongisc.backend.attendance.dto.AttendanceCheckInResponse;
import org.sejongisc.backend.attendance.entity.*;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AttendanceRoundCheckInTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;

    @Mock
    private AttendanceRoundRepository attendanceRoundRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    @DisplayName("라운드 기반 출석 체크인 성공 - 정상 출석")
    void checkInByRound_success_present() {
        // given
        UUID userId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User user = User.builder()
                .userId(userId)
                .name("테스트 사용자")
                .build();

        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("테스트 세션")
                .code("123456")
                .rewardPoints(10)
                .build();

        LocalTime now = LocalTime.now();
        AttendanceRound round = AttendanceRound.builder()
                .roundId(roundId)
                .attendanceSession(session)
                .roundDate(LocalDate.now())
                .startTime(now.minusMinutes(2))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.ACTIVE)
                .build();

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .build();

        Attendance savedAttendance = Attendance.builder()
                .attendanceId(UUID.randomUUID())
                .user(user)
                .attendanceSession(session)
                .attendanceRound(round)
                .attendanceStatus(AttendanceStatus.PRESENT)
                .checkedAt(LocalDateTime.now())
                .awardedPoints(10)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(attendanceRoundRepository.findRoundById(roundId)).thenReturn(Optional.of(round));
        when(attendanceRepository.findByAttendanceRound_RoundIdAndUser(roundId, user)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(Attendance.class))).thenReturn(savedAttendance);

        // when
        AttendanceCheckInResponse response = attendanceService.checkInByRound(request, userId);

        // then
        assertAll(
                () -> assertThat(response.getSuccess()).isTrue(),
                () -> assertThat(response.getStatus()).isEqualTo(AttendanceStatus.PRESENT.toString()),
                () -> assertThat(response.getRoundId()).isEqualTo(roundId),
                () -> assertThat(response.getAwardedPoints()).isEqualTo(10),
                () -> assertThat(response.getFailureReason()).isNull()
        );

        verify(attendanceRepository).save(any(Attendance.class));
    }

    @Test
    @DisplayName("라운드 기반 출석 체크인 성공 - 지각")
    void checkInByRound_success_late() {
        // given
        UUID userId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User user = User.builder()
                .userId(userId)
                .name("테스트 사용자")
                .build();

        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("테스트 세션")
                .code("123456")
                .rewardPoints(10)
                .build();

        LocalTime now = LocalTime.now();
        AttendanceRound round = AttendanceRound.builder()
                .roundId(roundId)
                .attendanceSession(session)
                .roundDate(LocalDate.now())
                .startTime(now.minusMinutes(10)) // 10분 전 시작
                .allowedMinutes(30)
                .roundStatus(RoundStatus.ACTIVE)
                .build();

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .build();

        Attendance savedAttendance = Attendance.builder()
                .attendanceId(UUID.randomUUID())
                .user(user)
                .attendanceSession(session)
                .attendanceRound(round)
                .attendanceStatus(AttendanceStatus.LATE)
                .checkedAt(LocalDateTime.now())
                .awardedPoints(10)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(attendanceRoundRepository.findRoundById(roundId)).thenReturn(Optional.of(round));
        when(attendanceRepository.findByAttendanceRound_RoundIdAndUser(roundId, user)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(Attendance.class))).thenReturn(savedAttendance);

        // when
        AttendanceCheckInResponse response = attendanceService.checkInByRound(request, userId);

        // then
        assertAll(
                () -> assertThat(response.getSuccess()).isTrue(),
                () -> assertThat(response.getStatus()).isEqualTo(AttendanceStatus.LATE.toString())
        );
    }

    @Test
    @DisplayName("라운드 기반 출석 체크인 실패 - 출석 시간 초과")
    void checkInByRound_fail_timeout() {
        // given
        UUID userId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();

        User user = User.builder()
                .userId(userId)
                .name("테스트 사용자")
                .build();

        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(UUID.randomUUID())
                .title("테스트 세션")
                .code("123456")
                .rewardPoints(10)
                .build();

        LocalTime now = LocalTime.now();
        AttendanceRound round = AttendanceRound.builder()
                .roundId(roundId)
                .attendanceSession(session)
                .roundDate(LocalDate.now())
                .startTime(now.minusMinutes(35)) // 35분 전 시작 (30분 + 5분)
                .allowedMinutes(30)
                .roundStatus(RoundStatus.CLOSED)
                .build();

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(attendanceRoundRepository.findRoundById(roundId)).thenReturn(Optional.of(round));

        // when
        AttendanceCheckInResponse response = attendanceService.checkInByRound(request, userId);

        // then
        assertAll(
                () -> assertThat(response.getSuccess()).isFalse(),
                () -> assertThat(response.getFailureReason()).isEqualTo("출석 시간 초과")
        );
    }

    @Test
    @DisplayName("라운드 기반 출석 체크인 실패 - 중복 출석")
    void checkInByRound_fail_duplicate() {
        // given
        UUID userId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();

        User user = User.builder()
                .userId(userId)
                .name("테스트 사용자")
                .build();

        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(UUID.randomUUID())
                .title("테스트 세션")
                .code("123456")
                .rewardPoints(10)
                .build();

        LocalTime now = LocalTime.now();
        AttendanceRound round = AttendanceRound.builder()
                .roundId(roundId)
                .attendanceSession(session)
                .roundDate(LocalDate.now())
                .startTime(now.minusMinutes(2))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.ACTIVE)
                .build();

        Attendance existingAttendance = Attendance.builder()
                .attendanceId(UUID.randomUUID())
                .user(user)
                .attendanceSession(session)
                .attendanceRound(round)
                .attendanceStatus(AttendanceStatus.PRESENT)
                .checkedAt(LocalDateTime.now())
                .build();

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(attendanceRoundRepository.findRoundById(roundId)).thenReturn(Optional.of(round));
        when(attendanceRepository.findByAttendanceRound_RoundIdAndUser(roundId, user))
                .thenReturn(Optional.of(existingAttendance));

        // when
        AttendanceCheckInResponse response = attendanceService.checkInByRound(request, userId);

        // then
        assertAll(
                () -> assertThat(response.getSuccess()).isFalse(),
                () -> assertThat(response.getFailureReason()).isEqualTo("이미 출석 체크인하셨습니다")
        );
    }

    @Test
    @DisplayName("라운드 기반 출석 체크인 실패 - 위치 불일치")
    void checkInByRound_fail_locationMismatch() {
        // given
        UUID userId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();

        User user = User.builder()
                .userId(userId)
                .name("테스트 사용자")
                .build();

        Location sessionLocation = Location.builder()
                .lat(37.5665)
                .lng(126.9780)
                .radiusMeters(100)
                .build();

        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(UUID.randomUUID())
                .title("테스트 세션")
                .code("123456")
                .rewardPoints(10)
                .location(sessionLocation)
                .build();

        LocalTime now = LocalTime.now();
        AttendanceRound round = AttendanceRound.builder()
                .roundId(roundId)
                .attendanceSession(session)
                .roundDate(LocalDate.now())
                .startTime(now.minusMinutes(2))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.ACTIVE)
                .build();

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.0000)  // 다른 위치
                .longitude(126.0000)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(attendanceRoundRepository.findRoundById(roundId)).thenReturn(Optional.of(round));
        when(attendanceRepository.findByAttendanceRound_RoundIdAndUser(roundId, user)).thenReturn(Optional.empty());

        // when
        AttendanceCheckInResponse response = attendanceService.checkInByRound(request, userId);

        // then
        assertAll(
                () -> assertThat(response.getSuccess()).isFalse(),
                () -> assertThat(response.getFailureReason()).contains("위치 불일치")
        );
    }

    @Test
    @DisplayName("라운드 기반 출석 체크인 실패 - 위치 정보 누락")
    void checkInByRound_fail_missingLocation() {
        // given
        UUID userId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();

        User user = User.builder()
                .userId(userId)
                .name("테스트 사용자")
                .build();

        Location sessionLocation = Location.builder()
                .lat(37.5665)
                .lng(126.9780)
                .radiusMeters(100)
                .build();

        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(UUID.randomUUID())
                .title("테스트 세션")
                .code("123456")
                .rewardPoints(10)
                .location(sessionLocation)
                .build();

        LocalTime now = LocalTime.now();
        AttendanceRound round = AttendanceRound.builder()
                .roundId(roundId)
                .attendanceSession(session)
                .roundDate(LocalDate.now())
                .startTime(now.minusMinutes(2))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.ACTIVE)
                .build();

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(attendanceRoundRepository.findRoundById(roundId)).thenReturn(Optional.of(round));
        when(attendanceRepository.findByAttendanceRound_RoundIdAndUser(roundId, user)).thenReturn(Optional.empty());

        // when
        AttendanceCheckInResponse response = attendanceService.checkInByRound(request, userId);

        // then
        assertAll(
                () -> assertThat(response.getSuccess()).isFalse(),
                () -> assertThat(response.getFailureReason()).isEqualTo("위치 정보가 필요합니다")
        );
    }
}
