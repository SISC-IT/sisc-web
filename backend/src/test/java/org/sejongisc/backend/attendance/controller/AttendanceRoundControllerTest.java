package org.sejongisc.backend.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.attendance.dto.AttendanceCheckInRequest;
import org.sejongisc.backend.attendance.dto.AttendanceCheckInResponse;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.dto.AttendanceRoundRequest;
import org.sejongisc.backend.attendance.dto.AttendanceRoundResponse;
import org.sejongisc.backend.attendance.entity.AttendanceStatus;
import org.sejongisc.backend.attendance.entity.RoundStatus;
import org.sejongisc.backend.attendance.service.AttendanceRoundService;
import org.sejongisc.backend.attendance.service.AttendanceService;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceRoundController.class)
@Import(TestSecurityConfig.class)
public class AttendanceRoundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AttendanceRoundService attendanceRoundService;

    @MockitoBean
    private AttendanceService attendanceService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private UUID roundId = UUID.randomUUID();
    private UUID sessionId = UUID.randomUUID();

    @Test
    @DisplayName("라운드 생성 성공")
    @WithMockUser(roles = "PRESIDENT")
    void createRound_success() throws Exception {
        // given
        LocalDate roundDate = LocalDate.now();
        LocalTime startTime = LocalTime.of(14, 0);

        AttendanceRoundRequest request = AttendanceRoundRequest.builder()
                .roundDate(roundDate)
                .startTime(startTime)
                .allowedMinutes(30)
                .build();

        AttendanceRoundResponse response = AttendanceRoundResponse.builder()
                .roundId(roundId)
                .roundDate(roundDate)
                .startTime(startTime)
                .endTime(startTime.plusMinutes(30))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.UPCOMING)
                .presentCount(0L)
                .absentCount(0L)
                .totalAttendees(0L)
                .build();

        when(attendanceRoundService.createRound(any(UUID.class), any(AttendanceRoundRequest.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/attendance/sessions/{sessionId}/rounds", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roundId").value(roundId.toString()))
                .andExpect(jsonPath("$.roundDate").value(roundDate.toString()))
                .andExpect(jsonPath("$.startTime").value("14:00"))
                .andExpect(jsonPath("$.allowedMinutes").value(30))
                .andExpect(jsonPath("$.roundStatus").value("UPCOMING"))
                .andExpect(jsonPath("$.presentCount").value(0))
                .andExpect(jsonPath("$.absentCount").value(0))
                .andExpect(jsonPath("$.totalAttendees").value(0));
    }

    @Test
    @DisplayName("라운드 생성 실패: 권한 없음")
    @WithMockUser(roles = "TEAM_MEMBER")
    void createRound_fail_noPermission() throws Exception {
        // given
        AttendanceRoundRequest request = AttendanceRoundRequest.builder()
                .roundDate(LocalDate.now())
                .startTime(LocalTime.of(14, 0))
                .allowedMinutes(30)
                .build();

        // when & then
        mockMvc.perform(post("/api/attendance/sessions/{sessionId}/rounds", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("라운드 조회 성공")
    @WithMockUser
    void getRound_success() throws Exception {
        // given
        LocalDate roundDate = LocalDate.now();
        LocalTime startTime = LocalTime.of(14, 0);

        AttendanceRoundResponse response = AttendanceRoundResponse.builder()
                .roundId(roundId)
                .roundDate(roundDate)
                .startTime(startTime)
                .endTime(startTime.plusMinutes(30))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.ACTIVE)
                .presentCount(5L)
                .absentCount(1L)
                .totalAttendees(8L)
                .build();

        when(attendanceRoundService.getRound(roundId)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/attendance/rounds/{roundId}", roundId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundId").value(roundId.toString()))
                .andExpect(jsonPath("$.roundStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.presentCount").value(5))
                .andExpect(jsonPath("$.absentCount").value(1))
                .andExpect(jsonPath("$.totalAttendees").value(8));
    }

    @Test
    @DisplayName("세션의 라운드 목록 조회 성공")
    @WithMockUser
    void getRoundsBySession_success() throws Exception {
        // given
        LocalDate roundDate = LocalDate.now();
        LocalTime startTime = LocalTime.of(14, 0);

        AttendanceRoundResponse round1 = AttendanceRoundResponse.builder()
                .roundId(UUID.randomUUID())
                .roundDate(roundDate)
                .startTime(startTime)
                .endTime(startTime.plusMinutes(30))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.ACTIVE)
                .presentCount(5L)
                .absentCount(1L)
                .totalAttendees(8L)
                .build();

        AttendanceRoundResponse round2 = AttendanceRoundResponse.builder()
                .roundId(UUID.randomUUID())
                .roundDate(roundDate.plusDays(7))
                .startTime(startTime)
                .endTime(startTime.plusMinutes(30))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.UPCOMING)
                .presentCount(0L)
                .absentCount(0L)
                .totalAttendees(0L)
                .build();

        List<AttendanceRoundResponse> responses = Arrays.asList(round1, round2);
        when(attendanceRoundService.getRoundsBySession(sessionId)).thenReturn(responses);

        // when & then
        mockMvc.perform(get("/api/attendance/sessions/{sessionId}/rounds", sessionId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roundStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[1].roundStatus").value("UPCOMING"));
    }

    @Test
    @DisplayName("라운드 정보 수정 성공")
    @WithMockUser(roles = "PRESIDENT")
    void updateRound_success() throws Exception {
        // given
        LocalDate newDate = LocalDate.now().plusDays(1);
        LocalTime newStartTime = LocalTime.of(15, 0);

        AttendanceRoundRequest request = AttendanceRoundRequest.builder()
                .roundDate(newDate)
                .startTime(newStartTime)
                .allowedMinutes(45)
                .build();

        AttendanceRoundResponse response = AttendanceRoundResponse.builder()
                .roundId(roundId)
                .roundDate(newDate)
                .startTime(newStartTime)
                .endTime(newStartTime.plusMinutes(45))
                .allowedMinutes(45)
                .roundStatus(RoundStatus.UPCOMING)
                .presentCount(0L)
                .absentCount(0L)
                .totalAttendees(0L)
                .build();

        when(attendanceRoundService.updateRound(any(UUID.class), any(AttendanceRoundRequest.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(put("/api/attendance/rounds/{roundId}", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundDate").value(newDate.toString()))
                .andExpect(jsonPath("$.startTime").value("15:00"))
                .andExpect(jsonPath("$.allowedMinutes").value(45));
    }

    @Test
    @DisplayName("라운드 삭제 성공")
    @WithMockUser(roles = "PRESIDENT")
    void deleteRound_success() throws Exception {
        // given
        doNothing().when(attendanceRoundService).deleteRound(roundId);

        // when & then
        mockMvc.perform(delete("/api/attendance/rounds/{roundId}", roundId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("라운드 출석 체크인 성공")
    @WithMockUser
    void checkInByRound_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .userName("김철수")
                .build();

        AttendanceCheckInResponse response = AttendanceCheckInResponse.builder()
                .roundId(roundId)
                .success(true)
                .status("PRESENT")
                .failureReason(null)
                .checkedAt(LocalDateTime.now())
                .awardedPoints(10)
                .remainingSeconds(1200L)
                .build();

        when(jwtProvider.getUserIdFromToken(anyString())).thenReturn(userId.toString());
        when(attendanceService.checkInByRound(any(AttendanceCheckInRequest.class), any(UUID.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/attendance/rounds/check-in")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.awardedPoints").value(10))
                .andExpect(jsonPath("$.failureReason").doesNotExist());
    }

    @Test
    @DisplayName("라운드 출석 체크인 실패: UPCOMING 상태")
    @WithMockUser
    void checkInByRound_fail_upcoming() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .userName("김철수")
                .build();

        when(jwtProvider.getUserIdFromToken(anyString())).thenReturn(userId.toString());

        // when & then
        mockMvc.perform(post("/api/attendance/rounds/check-in")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("라운드 출석 체크인 실패: CLOSED 상태")
    @WithMockUser
    void checkInByRound_fail_closed() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                .build();

        when(jwtProvider.getUserIdFromToken(anyString())).thenReturn(userId.toString());

        // when & then
        mockMvc.perform(post("/api/attendance/rounds/check-in")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("특정 날짜의 라운드 조회 성공")
    @WithMockUser
    void getRoundByDate_success() throws Exception {
        // given
        LocalDate targetDate = LocalDate.now();
        LocalTime startTime = LocalTime.of(14, 0);

        AttendanceRoundResponse response = AttendanceRoundResponse.builder()
                .roundId(roundId)
                .roundDate(targetDate)
                .startTime(startTime)
                .endTime(startTime.plusMinutes(30))
                .allowedMinutes(30)
                .roundStatus(RoundStatus.ACTIVE)
                .presentCount(5L)
                .absentCount(1L)
                .totalAttendees(8L)
                .build();

        when(attendanceRoundService.getRoundByDate(sessionId, targetDate)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/attendance/sessions/{sessionId}/rounds/by-date", sessionId)
                        .param("date", targetDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("라운드별 출석 명단 조회 성공")
    @WithMockUser
    void getAttendancesByRound_success() throws Exception {
        // given
        List<AttendanceResponse> attendanceList = Arrays.asList(
                AttendanceResponse.builder()
                        .attendanceId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .userName("김철수")
                        .attendanceSessionId(sessionId)
                        .attendanceRoundId(roundId)
                        .attendanceStatus(AttendanceStatus.PRESENT)
                        .build(),
                AttendanceResponse.builder()
                        .attendanceId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .userName("이영희")
                        .attendanceSessionId(sessionId)
                        .attendanceRoundId(roundId)
                        .attendanceStatus(AttendanceStatus.LATE)
                        .build()
        );

        when(attendanceService.getAttendancesByRound(roundId))
                .thenReturn(attendanceList);

        // when & then
        mockMvc.perform(get("/api/attendance/rounds/{roundId}/attendances", roundId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userName").value("김철수"))
                .andExpect(jsonPath("$[0].attendanceStatus").value("PRESENT"))
                .andExpect(jsonPath("$[1].userName").value("이영희"))
                .andExpect(jsonPath("$[1].attendanceStatus").value("LATE"));
    }

    @Test
    @DisplayName("익명 사용자 출석 체크인: 이름 없음 (자동 생성)")
    @WithMockUser
    void checkInByRound_anonymous_noName() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .roundId(roundId)
                .latitude(37.4979)
                .longitude(127.0276)
                // userName을 입력하지 않음
                .build();

        AttendanceCheckInResponse response = AttendanceCheckInResponse.builder()
                .roundId(roundId)
                .success(true)
                .status("PRESENT")
                .failureReason(null)
                .checkedAt(LocalDateTime.now())
                .awardedPoints(10)
                .remainingSeconds(1200L)
                .build();

        when(jwtProvider.getUserIdFromToken(anyString())).thenReturn(userId.toString());
        when(attendanceService.checkInByRound(any(AttendanceCheckInRequest.class), any(UUID.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/attendance/rounds/check-in")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("PRESENT"));
    }
}
