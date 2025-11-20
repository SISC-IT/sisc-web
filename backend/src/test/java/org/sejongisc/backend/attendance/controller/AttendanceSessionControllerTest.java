package org.sejongisc.backend.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.entity.SessionVisibility;
import org.sejongisc.backend.attendance.service.AttendanceSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceSessionController.class)
@Import(TestSecurityConfig.class)
public class AttendanceSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AttendanceSessionService attendanceSessionService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("출석 세션 생성 성공 (관리자)")
    @WithMockUser(roles = "PRESIDENT")
    void createSession_success() throws Exception {
        //given
        AttendanceSessionRequest request = AttendanceSessionRequest.builder()
                .title("세투연 정규세션")
                .tag("금융IT")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .rewardPoints(10)
                .latitude(37.5665)
                .longitude(126.9780)
                .radiusMeters(100)
                .visibility(SessionVisibility.PUBLIC)
                .build();

        AttendanceSessionResponse response = AttendanceSessionResponse.builder()
                .attendanceSessionId(UUID.randomUUID())
                .title("세투연 정규 세션")
                .windowSeconds(1800)
                .rewardPoints(10)
                .latitude(37.5665)
                .longitude(126.9780)
                .radiusMeters(100)
                .visibility(SessionVisibility.PUBLIC)
                .status(SessionStatus.UPCOMING)
                .participantCount(0)
                .build();

        when(attendanceSessionService.createSession(any(AttendanceSessionRequest.class))).thenReturn(response);

        //then
        mockMvc.perform(post("/api/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("세투연 정규 세션"))
                .andExpect(jsonPath("$.rewardPoints").value(10))
                .andExpect(jsonPath("$.latitude").value(37.5665))
                .andExpect(jsonPath("$.longitude").value(126.9780))
                .andExpect(jsonPath("$.radiusMeters").value(100))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.status").value("UPCOMING"))
                .andExpect(jsonPath("$.participantCount").value(0));
    }

    @Test
    @DisplayName("출석 세션 생성 실패: 권한 없음")
    @WithMockUser(roles = "TEAM_MEMBER")
    void createSession_fail_noPermission() throws Exception {
        //given
        AttendanceSessionRequest request = AttendanceSessionRequest.builder()
                .title("세투연 정규 세션")
                .startsAt(LocalDateTime.now().plusHours(1))
                .windowSeconds(1800)
                .build();

        //then
        mockMvc.perform(post("/api/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

    }

    @Test
    @DisplayName("출석 세션 생성 실패: 유혀성 검증 오류")
    @WithMockUser(roles = "PRESIDENT")
    void createSession_fail_validation() throws Exception {
        //given
        AttendanceSessionRequest request = AttendanceSessionRequest.builder()
                .title("")
                .startsAt(LocalDateTime.now().minusHours(1))
                .windowSeconds(100)
                .latitude(91.0)
                .build();

        //then
        mockMvc.perform(post("/api/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("세션 상세 조회 성공")
    @WithMockUser
    void getSession_success() throws Exception {
        //given
        UUID sessionId = UUID.randomUUID();
        AttendanceSessionResponse response = AttendanceSessionResponse.builder()
                .attendanceSessionId(sessionId)
                .title("세투연 정규 세션")
                .windowSeconds(1800)
                .status(SessionStatus.UPCOMING)
                .participantCount(0)
                .build();

        when(attendanceSessionService.getSessionById(sessionId)).thenReturn(response);

        //then
        mockMvc.perform(get("/api/attendance/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceSessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.title").value("세투연 정규 세션"));
    }

    @Test
    @DisplayName("공개 세션 목록 조회 성공")
    @WithMockUser
    void getPublicSessions_success() throws Exception {
        //given
        List<AttendanceSessionResponse> responses = Arrays.asList(
                AttendanceSessionResponse.builder()
                        .attendanceSessionId(UUID.randomUUID())
                        .title("정규 세션 1")
                        .status(SessionStatus.UPCOMING)
                        .visibility(SessionVisibility.PUBLIC)
                        .participantCount(10)
                        .build(),
                AttendanceSessionResponse.builder()
                        .attendanceSessionId(UUID.randomUUID())
                        .title("정규 세션 2")
                        .status(SessionStatus.OPEN)
                        .visibility(SessionVisibility.PUBLIC)
                        .participantCount(5)
                        .build()
        );

        when(attendanceSessionService.getPublicSessions()).thenReturn(responses);

        //then
        mockMvc.perform(get("/api/attendance/sessions/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("정규 세션 1"))
                .andExpect(jsonPath("$[0].participantCount").value(10))
                .andExpect(jsonPath("$[1].title").value("정규 세션 2"))
                .andExpect(jsonPath("$[1].participantCount").value(5));
    }

    @Test
    @DisplayName("활성 세션 목록 조회 성공")
    @WithMockUser
    void getActivateSessions_success() throws Exception {
        //given
        List<AttendanceSessionResponse> responses = Arrays.asList(
                AttendanceSessionResponse.builder()
                        .attendanceSessionId(UUID.randomUUID())
                        .title("활성 세션")
                        .status(SessionStatus.OPEN)
                        .checkInAvailable(true)
                        .remainingSeconds(900L)
                        .participantCount(8)
                        .build()
        );

        when(attendanceSessionService.getActiveSessions()).thenReturn(responses);

        //then
        mockMvc.perform(get("/api/attendance/sessions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("활성 세션"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].checkInAvailable").value(true))
                .andExpect(jsonPath("$[0].remainingSeconds").value(900))
                .andExpect(jsonPath("$[0].participantCount").value(8));
    }

    @Test
    @DisplayName("세션 수정 성공 (관리자)")
    @WithMockUser(roles = "PRESIDENT")
    void updateSession_success() throws Exception {
        //given
        UUID sessionId = UUID.randomUUID();
        AttendanceSessionRequest request = AttendanceSessionRequest.builder()
                .title("수정된 제목")
                .tag("수정된 태그")
                .startsAt(LocalDateTime.now().plusHours(2))
                .windowSeconds(3600)
                .rewardPoints(10)
                .visibility(SessionVisibility.PRIVATE)
                .build();

        AttendanceSessionResponse response = AttendanceSessionResponse.builder()
                .attendanceSessionId(sessionId)
                .title("수정된 제목")
                .windowSeconds(3600)
                .rewardPoints(10)
                .visibility(SessionVisibility.PRIVATE)
                .status(SessionStatus.UPCOMING)
                .participantCount(0)
                .build();

        when(attendanceSessionService.updateSession(eq(sessionId), any(AttendanceSessionRequest.class))).thenReturn(response);

        //then
        mockMvc.perform(put("/api/attendance/sessions/{sessionId}", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.rewardPoints").value(10))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @Test
    @DisplayName("세션 활성화 성공")
    @WithMockUser(roles = "PRESIDENT")
    void activateSession_success() throws Exception {
        //given
        UUID sessionId = UUID.randomUUID();
        doNothing().when(attendanceSessionService).activateSession(sessionId);

        //then
        mockMvc.perform(post("/api/attendance/sessions/{sessionId}/activate", sessionId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("세션 종료 성공 (관리자)")
    @WithMockUser(roles = "PRESIDENT")
    void closeSession_success() throws Exception {
        //given
        UUID sessionId = UUID.randomUUID();
        doNothing().when(attendanceSessionService).closeSession(sessionId);

        //then
        mockMvc.perform(post("/api/attendance/sessions/{sessionId}/close", sessionId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("세션 삭제 성공 (관리자)")
    @WithMockUser(roles = "PRESIDENT")
    void deleteSession_success() throws Exception {
        //given
        UUID sessionId = UUID.randomUUID();
        doNothing().when(attendanceSessionService).deleteSession(sessionId);

        //then
        mockMvc.perform(delete("/api/attendance/sessions/{sessionId}", sessionId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("관리자 전용 기능 접근 실패: 권한 없음")
    @WithMockUser(roles = "TEAM_MEMBER")
    void adminOnlyEndpoints_fail_noPermission() throws Exception {
        UUID sessionId = UUID.randomUUID();

        // 세션 수정
        mockMvc.perform(put("/api/attendance/sessions/{sessionId}", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // 세션 활성화
        mockMvc.perform(post("/api/attendance/sessions/{sessionId}/activate", sessionId))
                .andExpect(status().isForbidden());

        // 세션 종료
        mockMvc.perform(post("/api/attendance/sessions/{sessionId}/close", sessionId))
                .andExpect(status().isForbidden());

        // 세션 삭제
        mockMvc.perform(delete("/api/attendance/sessions/{sessionId}", sessionId))
                .andExpect(status().isForbidden());
    }
}
