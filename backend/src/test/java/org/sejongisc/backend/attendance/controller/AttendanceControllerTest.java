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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

        when(attendanceService.checkIn(any(UUID.class), any(AttendanceRequest.class), any(User.class))).thenReturn(response);

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
}
