package org.sejongisc.backend.attendance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.attendance.dto.SessionLocationUpdateRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.Location;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SessionLocationUpdateTest {

    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @InjectMocks
    private AttendanceSessionService attendanceSessionService;

    @Test
    @DisplayName("세션 위치 재설정 성공 - 기존 위치 있음")
    void updateSessionLocation_success_withExistingLocation() {
        // given
        UUID sessionId = UUID.randomUUID();
        Double newLatitude = 37.4979;
        Double newLongitude = 127.0276;

        SessionLocationUpdateRequest request = SessionLocationUpdateRequest.builder()
                .latitude(newLatitude)
                .longitude(newLongitude)
                .build();

        Location existingLocation = Location.builder()
                .lat(37.5665)
                .lng(126.9780)
                .radiusMeters(200)
                .build();

        AttendanceSession existingSession = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("테스트 세션")
                .code("123456")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .location(existingLocation)
                .status(SessionStatus.UPCOMING)
                .build();

        AttendanceSession updatedSession = existingSession.toBuilder()
                .location(Location.builder()
                        .lat(newLatitude)
                        .lng(newLongitude)
                        .radiusMeters(200) // 기존 반경 유지
                        .build())
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession));
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenReturn(updatedSession);
        when(attendanceRepository.countByAttendanceSession(any(AttendanceSession.class))).thenReturn(0L);

        // when
        AttendanceSessionResponse response = attendanceSessionService.updateSessionLocation(sessionId, request);

        // then
        assertAll(
                () -> assertThat(response.getLatitude()).isEqualTo(newLatitude),
                () -> assertThat(response.getLongitude()).isEqualTo(newLongitude),
                () -> assertThat(response.getRadiusMeters()).isEqualTo(200)
        );

        verify(attendanceSessionRepository).save(any(AttendanceSession.class));
    }

    @Test
    @DisplayName("세션 위치 재설정 성공 - 기존 위치 없음")
    void updateSessionLocation_success_withoutExistingLocation() {
        // given
        UUID sessionId = UUID.randomUUID();
        Double newLatitude = 37.4979;
        Double newLongitude = 127.0276;

        SessionLocationUpdateRequest request = SessionLocationUpdateRequest.builder()
                .latitude(newLatitude)
                .longitude(newLongitude)
                .build();

        AttendanceSession existingSession = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("테스트 세션")
                .code("123456")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .location(null) // 기존 위치 없음
                .status(SessionStatus.UPCOMING)
                .build();

        AttendanceSession updatedSession = existingSession.toBuilder()
                .location(Location.builder()
                        .lat(newLatitude)
                        .lng(newLongitude)
                        .radiusMeters(100) // 기본값 100m
                        .build())
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession));
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenReturn(updatedSession);
        when(attendanceRepository.countByAttendanceSession(any(AttendanceSession.class))).thenReturn(0L);

        // when
        AttendanceSessionResponse response = attendanceSessionService.updateSessionLocation(sessionId, request);

        // then
        assertAll(
                () -> assertThat(response.getLatitude()).isEqualTo(newLatitude),
                () -> assertThat(response.getLongitude()).isEqualTo(newLongitude),
                () -> assertThat(response.getRadiusMeters()).isEqualTo(100)
        );

        verify(attendanceSessionRepository).save(any(AttendanceSession.class));
    }

    @Test
    @DisplayName("세션 위치 재설정 실패 - 존재하지 않는 세션")
    void updateSessionLocation_fail_sessionNotFound() {
        // given
        UUID sessionId = UUID.randomUUID();
        SessionLocationUpdateRequest request = SessionLocationUpdateRequest.builder()
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> attendanceSessionService.updateSessionLocation(sessionId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 세션입니다: " + sessionId);
    }

    @Test
    @DisplayName("세션 위치 재설정 - 반경 유지 확인")
    void updateSessionLocation_verifyRadiusPreservation() {
        // given
        UUID sessionId = UUID.randomUUID();
        int customRadius = 500;

        SessionLocationUpdateRequest request = SessionLocationUpdateRequest.builder()
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        Location existingLocation = Location.builder()
                .lat(37.5665)
                .lng(126.9780)
                .radiusMeters(customRadius)
                .build();

        AttendanceSession existingSession = AttendanceSession.builder()
                .attendanceSessionId(sessionId)
                .title("테스트 세션")
                .code("123456")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .location(existingLocation)
                .status(SessionStatus.UPCOMING)
                .build();

        AttendanceSession updatedSession = existingSession.toBuilder()
                .location(Location.builder()
                        .lat(37.4979)
                        .lng(127.0276)
                        .radiusMeters(customRadius)
                        .build())
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession));
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenReturn(updatedSession);
        when(attendanceRepository.countByAttendanceSession(any(AttendanceSession.class))).thenReturn(0L);

        // when
        AttendanceSessionResponse response = attendanceSessionService.updateSessionLocation(sessionId, request);

        // then
        assertThat(response.getRadiusMeters()).isEqualTo(customRadius);
    }
}
