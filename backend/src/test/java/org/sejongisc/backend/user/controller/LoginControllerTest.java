package org.sejongisc.backend.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.auth.controller.LoginController;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.exception.controller.GlobalExceptionHandler;
import org.sejongisc.backend.auth.dto.LoginRequest;
import org.sejongisc.backend.auth.dto.LoginResponse;
import org.sejongisc.backend.auth.service.LoginService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

    @Mock
    LoginService loginService;

    @InjectMocks
    LoginController loginController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders.standaloneSetup(loginController)
                .setControllerAdvice(new GlobalExceptionHandler()) // 👈 예외 핸들러 수동 등록
                .build();
    }

    @Test
    @DisplayName("POST /auth/login - 로그인 성공 시 200 OK")
    void login_success() throws Exception {
        LoginRequest req = new LoginRequest("hong@example.com", "Password123!");
        LoginResponse resp = LoginResponse.builder()
                .accessToken("mockAccessToken")
                .userId(UUID.randomUUID())
                .email("hong@example.com")
                .name("홍길동")
                .role(org.sejongisc.backend.user.entity.Role.TEAM_MEMBER)
                .point(100)
                .build();

        when(loginService.login(any(LoginRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("mockAccessToken")))
                .andExpect(jsonPath("$.email", is("hong@example.com")))
                .andExpect(jsonPath("$.name", is("홍길동")))
                .andExpect(jsonPath("$.role", is("TEAM_MEMBER")))
                .andExpect(jsonPath("$.point", is(100)));
    }

    @Test
    @DisplayName("POST /auth/login - 존재하지 않는 사용자면 404 반환")
    void login_userNotFound() throws Exception {
        LoginRequest req = new LoginRequest("notfound@example.com", "Password123!");

        when(loginService.login(any(LoginRequest.class)))
                .thenThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("USER_NOT_FOUND")));
    }

    @Test
    @DisplayName("POST /auth/login - 비밀번호 틀리면 401 반환")
    void login_wrongPassword() throws Exception {
        LoginRequest req = new LoginRequest("hong@example.com", "WrongPassword!");

        when(loginService.login(any(LoginRequest.class)))
                .thenThrow(new CustomException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }
}
