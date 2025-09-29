package org.sejongisc.backend.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.user.dto.KakaoUserInfoResponse;
import org.sejongisc.backend.user.dto.LoginResponse;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.service.KakaoService;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class KakaoAuthLoginControllerTest {

    @Mock
    KakaoService kakaoService;

    @Mock
    UserService userService;

    @Mock
    JwtProvider jwtProvider;

    @InjectMocks
    KakaoAuthLoginController kakaoAuthLoginController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders.standaloneSetup(kakaoAuthLoginController)
                .build();
    }

    @Test
    @DisplayName("GET /auth/login/kakao - 카카오 로그인 성공 시 200 OK & 쿠키/응답 반환")
    void kakaoLogin_success() throws Exception {
        // given
        String code = "test-code";
        String kakaoAccessToken = "mock-kakao-token";
        UUID userId = UUID.randomUUID();
        String jwt = "mock-jwt-token";

        // 카카오 응답 객체
        KakaoUserInfoResponse userInfo = new KakaoUserInfoResponse();
        userInfo.id = 12345L;

        // DB User
        User user = User.builder()
                .userId(userId)
                .name("홍길동")
                .phoneNumber("01012345678")
                .role(Role.TEAM_MEMBER)
                .point(200)
                .build();

        // mock 동작 정의
        when(kakaoService.getAccessTokenFromKakao(eq(code))).thenReturn(kakaoAccessToken);
        when(kakaoService.getUserInfo(eq(kakaoAccessToken))).thenReturn(userInfo);
        when(userService.findOrCreateUser(any(KakaoUserInfoResponse.class))).thenReturn(user);
        when(jwtProvider.createToken(userId, Role.TEAM_MEMBER)).thenReturn(jwt);

        // when & then
        mockMvc.perform(get("/auth/login/kakao")
                        .param("code", code)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE)) // 쿠키 존재 확인
                .andExpect(jsonPath("$.accessToken", is(jwt)))
                .andExpect(jsonPath("$.userId", is(userId.toString())))
                .andExpect(jsonPath("$.name", is("홍길동")))
                .andExpect(jsonPath("$.role", is("TEAM_MEMBER")))
                .andExpect(jsonPath("$.phoneNumber", is("01012345678")))
                .andExpect(jsonPath("$.point", is(200)));
    }
}
