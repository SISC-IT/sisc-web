package org.sejongisc.backend.attendance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.attendance.dto.AttendanceRoundRequest;
import org.sejongisc.backend.attendance.dto.AttendanceRoundResponse;
import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.RoundStatus;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AttendanceRoundServiceTest {

    @Mock
    private AttendanceRoundRepository attendanceRoundRepository;

    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;

    @InjectMocks
    private AttendanceRoundService attendanceRoundService;

    @Test
    @DisplayName("라운드 생성 성공")
    void createRound_success() {
        // given
        UUID sessionId = UUID.randomUUID();
        LocalDate roundDate = LocalDate.now().plusDays(1);
        LocalTime startTime = LocalTime.of(14, 0);

        AttendanceRoundRequest request = AttendanceRoundRequest.builder()
                .date(roundDate)
                .startTime(startTime)
                .availableMinutes(30)
                .build();

        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("테스트 세션")
                .code("123456")
                .build();

        AttendanceRound savedRound = AttendanceRound.builder()
                .roundId(UUID.randomUUID())
                .attendanceSession(session)
                .roundDate(roundDate)
                .startTime(startTime)
                .allowedMinutes(30)
                .roundStatus(RoundStatus.UPCOMING)
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(attendanceRoundRepository.save(any(AttendanceRound.class))).thenReturn(savedRound);

        // when
        AttendanceRoundResponse response = attendanceRoundService.createRound(sessionId, request);

        // then
        assertAll(
                () -> assertThat(response.getRoundId()).isNotNull(),
                () -> assertThat(response.getDate()).isEqualTo(roundDate),
                () -> assertThat(response.getStartTime()).isEqualTo(startTime),
                () -> assertThat(response.getAvailableMinutes()).isEqualTo(30)
        );

        verify(attendanceSessionRepository).findById(sessionId);
        verify(attendanceRoundRepository).save(any(AttendanceRound.class));
    }

    @Test
    @DisplayName("라운드 생성 실패: 존재하지 않는 세션")
    void createRound_fail_sessionNotFound() {
        // given
        UUID sessionId = UUID.randomUUID();
        AttendanceRoundRequest request = AttendanceRoundRequest.builder()
                .date(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(14, 0))
                .availableMinutes(30)
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> attendanceRoundService.createRound(sessionId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("세션을 찾을 수 없습니다: " + sessionId);
    }

    @Test
    @DisplayName("라운드 조회 성공")
    void getRound_success() {
        // given
        UUID roundId = UUID.randomUUID();
        LocalDate roundDate = LocalDate.now().plusDays(1);
        LocalTime startTime = LocalTime.of(14, 0);

        AttendanceRound round = AttendanceRound.builder()
                .roundId(roundId)
                .roundDate(roundDate)
                .startTime(startTime)
                .allowedMinutes(30)
                .roundStatus(RoundStatus.UPCOMING)
                .build();

        when(attendanceRoundRepository.findRoundById(roundId)).thenReturn(Optional.of(round));

        // when
        AttendanceRoundResponse response = attendanceRoundService.getRound(roundId);

        // then
        assertAll(
                () -> assertThat(response.getRoundId()).isEqualTo(roundId),
                () -> assertThat(response.getDate()).isEqualTo(roundDate),
                () -> assertThat(response.getStartTime()).isEqualTo(startTime)
        );
    }

    @Test
    @DisplayName("라운드 조회 실패: 존재하지 않는 라운드")
    void getRound_fail_roundNotFound() {
        // given
        UUID roundId = UUID.randomUUID();
        when(attendanceRoundRepository.findRoundById(roundId)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> attendanceRoundService.getRound(roundId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("라운드를 찾을 수 없습니다: " + roundId);
    }

    @Test
    @DisplayName("세션별 라운드 목록 조회 성공")
    void getRoundsBySession_success() {
        // given
        UUID sessionId = UUID.randomUUID();
        LocalDate date1 = LocalDate.now().plusDays(1);
        LocalDate date2 = LocalDate.now().plusDays(2);
        LocalTime time = LocalTime.of(14, 0);

        List<AttendanceRound> rounds = Arrays.asList(
                AttendanceRound.builder()
                        .roundId(UUID.randomUUID())
                        .roundDate(date1)
                        .startTime(time)
                        .allowedMinutes(30)
                        .roundStatus(RoundStatus.UPCOMING)
                        .build(),
                AttendanceRound.builder()
                        .roundId(UUID.randomUUID())
                        .roundDate(date2)
                        .startTime(time)
                        .allowedMinutes(30)
                        .roundStatus(RoundStatus.UPCOMING)
                        .build()
        );

        when(attendanceRoundRepository.findByAttendanceSession_AttendanceSessionIdOrderByRoundDateAsc(sessionId))
                .thenReturn(rounds);

        // when
        List<AttendanceRoundResponse> responses = attendanceRoundService.getRoundsBySession(sessionId);

        // then
        assertAll(
                () -> assertThat(responses).hasSize(2),
                () -> assertThat(responses.get(0).getDate()).isEqualTo(date1),
                () -> assertThat(responses.get(1).getDate()).isEqualTo(date2)
        );
    }

    @Test
    @DisplayName("라운드 수정 성공")
    void updateRound_success() {
        // given
        UUID roundId = UUID.randomUUID();
        LocalDate newDate = LocalDate.now().plusDays(3);
        LocalTime newTime = LocalTime.of(15, 0);

        AttendanceRoundRequest request = AttendanceRoundRequest.builder()
                .date(newDate)
                .startTime(newTime)
                .availableMinutes(45)
                .build();

        AttendanceRound existingRound = AttendanceRound.builder()
                .roundId(roundId)
                .roundDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(14, 0))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.UPCOMING)
                .build();

        AttendanceRound updatedRound = existingRound.toBuilder()
                .roundDate(newDate)
                .startTime(newTime)
                .allowedMinutes(45)
                .build();

        when(attendanceRoundRepository.findRoundById(roundId)).thenReturn(Optional.of(existingRound));
        when(attendanceRoundRepository.save(any(AttendanceRound.class))).thenReturn(updatedRound);

        // when
        AttendanceRoundResponse response = attendanceRoundService.updateRound(roundId, request);

        // then
        assertAll(
                () -> assertThat(response.getDate()).isEqualTo(newDate),
                () -> assertThat(response.getStartTime()).isEqualTo(newTime),
                () -> assertThat(response.getAvailableMinutes()).isEqualTo(45)
        );

        verify(attendanceRoundRepository).save(any(AttendanceRound.class));
    }

    @Test
    @DisplayName("라운드 삭제 성공")
    void deleteRound_success() {
        // given
        UUID roundId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        AttendanceSession session = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("테스트 세션")
                .build();

        AttendanceRound round = AttendanceRound.builder()
                .roundId(roundId)
                .attendanceSession(session)
                .roundDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(14, 0))
                .build();

        when(attendanceRoundRepository.findRoundById(roundId)).thenReturn(Optional.of(round));

        // when
        attendanceRoundService.deleteRound(roundId);

        // then
        verify(attendanceRoundRepository).delete(round);
    }

    @Test
    @DisplayName("특정 날짜의 라운드 조회 성공")
    void getRoundByDate_success() {
        // given
        UUID sessionId = UUID.randomUUID();
        LocalDate targetDate = LocalDate.now().plusDays(1);

        AttendanceRound round = AttendanceRound.builder()
                .roundId(UUID.randomUUID())
                .roundDate(targetDate)
                .startTime(LocalTime.of(14, 0))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.UPCOMING)
                .build();

        when(attendanceRoundRepository.findByAttendanceSession_AttendanceSessionIdAndRoundDate(sessionId, targetDate))
                .thenReturn(Optional.of(round));

        // when
        AttendanceRoundResponse response = attendanceRoundService.getRoundByDate(sessionId, targetDate);

        // then
        assertAll(
                () -> assertThat(response.getDate()).isEqualTo(targetDate),
                () -> assertThat(response.getStartTime()).isEqualTo(LocalTime.of(14, 0))
        );
    }

    @Test
    @DisplayName("특정 날짜의 라운드 조회 실패: 존재하지 않는 라운드")
    void getRoundByDate_fail_roundNotFound() {
        // given
        UUID sessionId = UUID.randomUUID();
        LocalDate targetDate = LocalDate.now().plusDays(1);

        when(attendanceRoundRepository.findByAttendanceSession_AttendanceSessionIdAndRoundDate(sessionId, targetDate))
                .thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> attendanceRoundService.getRoundByDate(sessionId, targetDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 날짜의 라운드를 찾을 수 없습니다");
    }
}
