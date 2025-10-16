package org.sejongisc.backend.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.attendance.dto.AttendanceRequest;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.entity.Attendance;
import org.sejongisc.backend.attendance.entity.AttendanceStatus;
import org.sejongisc.backend.attendance.service.AttendanceService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceController.class)
@Import(TestSecurityConfig.class)
public class AttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AttendanceService attendanceService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("출석 체크인 성공")
    @WithMockUser
    void checkIn_success() throws Exception {
        //given
        AttendanceRequest request = AttendanceRequest.builder()
                .code("123456")
                .latitude(37.5665)
                .longitude(126.9780)
                .note("정상 춣석")
                .deviceInfo("iphone 14")
                .build();

        User user = User.builder()
                .userId(UUID.randomUUID())
                .name("오찬혁")
                .email("oh@example.com")
                .role(Role.TEAM_MEMBER)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(user);

        AttendanceResponse response = AttendanceResponse.builder()
                .attendanceId(UUID.randomUUID())
                .userId(user.getUserId())
                .userName("오찬혁")
                .attendanceSessionId(UUID.randomUUID())
                .attendanceStatus(AttendanceStatus.PRESENT)
                .checkedAt(LocalDateTime.now())
                .awardedPoints(10)
                .note("정상 출석")
                .deviceInfo("iphone 14")
                .isLate(false)
                .build();

        when(attendanceService.checkIn(any(UUID.class), any(AttendanceRequest.class), eq(user.getUserId()))).thenReturn(response);

        //then
        UUID sessionId = UUID.randomUUID();
        mockMvc.perform(post("/api/attendance/sessions/{sessionId}/check-in", sessionId)
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userName").value("오찬혁"))
                .andExpect(jsonPath("$.attendanceStatus").value("PRESENT"))
                .andExpect(jsonPath("$.awardedPoints").value(10))
                .andExpect(jsonPath("$.note").value("정상 출석"))
                .andExpect(jsonPath("$.deviceInfo").value("iphone 14"))
                .andExpect(jsonPath("$.late").value(false));
    }

    @Test
    @DisplayName("출석 체크인 실패: 유효성 검증 오류")
    @WithMockUser
    void checkIn_fail_validation() throws Exception {
        //given
        AttendanceRequest request = AttendanceRequest.builder()
                .code("12345")
                .latitude(91.0)
                .longitude(181.0)
                .build();

        User user = User.builder()
                .userId(UUID.randomUUID())
                .name("오찬혁")
                .email("oh@example.com")
                .role(Role.TEAM_MEMBER)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(user);

        //then
        UUID sessionId = UUID.randomUUID();
        mockMvc.perform(post("/api/attendance/sessions/{sessionId}/check-in", sessionId)
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("내 출석 기록 조회 성공")
    @WithMockUser
    void getMyAttendances_success() throws Exception {
        //given
        User user = User.builder()
                .userId(UUID.randomUUID())
                .name("오찬혁")
                .email("oh@example.com")
                .role(Role.TEAM_MEMBER)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(user);

        List<AttendanceResponse> responses = Arrays.asList(
                AttendanceResponse.builder()
                        .attendanceId(UUID.randomUUID())
                        .userId(user.getUserId())
                        .userName("오찬혁")
                        .attendanceSessionId(UUID.randomUUID())
                        .attendanceStatus(AttendanceStatus.PRESENT)
                        .checkedAt(LocalDateTime.now().minusDays(1))
                        .awardedPoints(10)
                        .build(),
                AttendanceResponse.builder()
                        .attendanceId(UUID.randomUUID())
                        .userId(user.getUserId())
                        .userName("오찬혁")
                        .attendanceSessionId(UUID.randomUUID())
                        .attendanceStatus(AttendanceStatus.LATE)
                        .checkedAt(LocalDateTime.now())
                        .awardedPoints(5)
                        .build()
        );

        when(attendanceService.getAttendancesByUser(eq(user.getUserId()))).thenReturn(responses);

        //then
        mockMvc.perform(get("/api/attendance/history")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].attendanceStatus").value("PRESENT"))
                .andExpect(jsonPath("$[0].awardedPoints").value(10))
                .andExpect(jsonPath("$[1].attendanceStatus").value("LATE"))
                .andExpect(jsonPath("$[1].awardedPoints").value(5));
    }

    @Test
    @DisplayName("세션별 출석 목록 조회 성공 (관리자)")
    @WithMockUser(roles = "PRESIDENT")
    void getAttendancesBySession_success() throws Exception {
        //given
        UUID sessionId = UUID.randomUUID();
        List<AttendanceResponse> responses = Arrays.asList(
                AttendanceResponse.builder()
                        .attendanceId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .userName("오찬혁")
                        .attendanceSessionId(sessionId)
                        .attendanceStatus(AttendanceStatus.PRESENT)
                        .checkedAt(LocalDateTime.now())
                        .awardedPoints(10)
                        .build(),
                AttendanceResponse.builder()
                        .attendanceId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .userName("김찬혁")
                        .attendanceSessionId(sessionId)
                        .attendanceStatus(AttendanceStatus.LATE)
                        .checkedAt(LocalDateTime.now())
                        .awardedPoints(5)
                        .build()
        );

        when(attendanceService.getAttendancesBySession(sessionId)).thenReturn(responses);

        //then
        mockMvc.perform(get("/api/attendance/sessions/{sessionId}/attendances", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userName").value("오찬혁"))
                .andExpect(jsonPath("$[0].attendanceStatus").value("PRESENT"))
                .andExpect(jsonPath("$[1].userName").value("김찬혁"))
                .andExpect(jsonPath("$[1].attendanceStatus").value("LATE"));
    }

    @Test
    @DisplayName("세션별 출석 목록 조회 실패: 권한 없음")
    void getAttendancesBySession_fail_noPermission() throws Exception {
        //given
        UUID sessionId = UUID.randomUUID();

        User teamMemberUser = User.builder()
                .userId(UUID.randomUUID())
                .name("멤버")
                .email("member@example.com")
                .role(Role.TEAM_MEMBER)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(teamMemberUser);

        //then
        mockMvc.perform(get("/api/attendance/sessions/{sessionId}/attendances", sessionId)
                        .with(user(userDetails)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("출석 상태 수정 성공 (관리자)")
    void updateAttendanceStatus_success() throws Exception {
        //given
        UUID attendanceId = UUID.randomUUID();
        String status = "PRESENT";
        String reason = "관리자 수정";

        User adminUser = User.builder()
                .userId(UUID.randomUUID())
                .name("관리자")
                .email("admin@example.com")
                .role(Role.PRESIDENT)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(adminUser);

        AttendanceResponse response = AttendanceResponse.builder()
                .attendanceId(attendanceId)
                .userId(UUID.randomUUID())
                .userName("오찬혁")
                .attendanceSessionId(UUID.randomUUID())
                .attendanceStatus(AttendanceStatus.PRESENT)
                .checkedAt(LocalDateTime.now())
                .awardedPoints(10)
                .note(reason)
                .build();

        when(attendanceService.updateAttendanceStatus(
                any(UUID.class), eq(attendanceId), eq(status), eq(reason), eq(adminUser.getUserId())
        )).thenReturn(response);

        //then
        UUID sessionId = UUID.randomUUID();
        mockMvc.perform(post("/api/attendance/sessions/{sessionId}/attendances/{memberId}", sessionId, attendanceId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_PRESIDENT"))
                        )))
                        .param("status", status)
                        .param("reason", reason))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceStatus").value("PRESENT"))
                .andExpect(jsonPath("$.note").value(reason));
    }

    @Test
    @DisplayName("출석 상태 수정 실패: 권한 없음")
    void updateAttendanceStatus_fail_noPermission() throws  Exception {
        //given
        UUID attendanceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User teamMemberUser = User.builder()
                .userId(UUID.randomUUID())
                .name("member@example.com")
                .role(Role.TEAM_MEMBER)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(teamMemberUser);

        //then
        mockMvc.perform(post("/api/attendance/sessions/{sessionId}/attendances/{memberId}", sessionId, attendanceId)
                        .with(user(userDetails))
                        .param("status", "PRESENT")
                        .param("reason", "사유"))
                .andExpect(status().isForbidden());

    }
}
