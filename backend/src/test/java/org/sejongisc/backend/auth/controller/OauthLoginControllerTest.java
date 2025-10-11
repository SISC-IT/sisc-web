package org.sejongisc.backend.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.auth.dto.*;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.auth.service.GithubService;
import org.sejongisc.backend.auth.service.GoogleService;
import org.sejongisc.backend.auth.service.KakaoService;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OauthLoginControllerTest {

    @Mock GoogleService googleService;
    @Mock KakaoService kakaoService;
    @Mock GithubService githubService;
    @Mock UserService userService;
    @Mock JwtProvider jwtProvider;

    @InjectMocks
    OauthLoginController oauthLoginController;

    MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(oauthLoginController).build();
    }

    @Test
    @DisplayName("GET /auth/login/GOOGLE - 구글 로그인 성공")
    void googleLogin_success() throws Exception {
        // given: JSON 문자열을 바로 Mock 응답으로 매핑
        String tokenJson = """
            {
              "access_token": "mock-google-access-token",
              "refresh_token": "mock-refresh-token",
              "id_token": "mock-id-token",
              "scope": "openid email profile",
              "token_type": "Bearer"
            }
            """;
        GoogleTokenResponse tokenResponse =
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(tokenJson, GoogleTokenResponse.class);

        String userInfoJson = """
            {
              "sub": "12345",
              "email": "google@test.com",
              "name": "구글유저",
              "picture": "http://example.com/pic.png"
            }
            """;
        GoogleUserInfoResponse userInfo =
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(userInfoJson, GoogleUserInfoResponse.class);

        User mockUser = User.builder()
                .userId(UUID.randomUUID())
                .name("구글유저")
                .email("google@test.com")
                .role(Role.TEAM_MEMBER)
                .build();

        when(googleService.getAccessTokenFromGoogle("test-code")).thenReturn(tokenResponse);
        when(googleService.getUserInfo("mock-google-access-token")).thenReturn(userInfo);
        when(userService.findOrCreateUser(any())).thenReturn(mockUser);
        when(jwtProvider.createToken(mockUser.getUserId(), mockUser.getRole()))
                .thenReturn("mock-jwt-token");

        // when & then
        mockMvc.perform(get("/auth/login/GOOGLE")
                        .param("code", "test-code")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.userId").value(mockUser.getUserId().toString()))
                .andExpect(jsonPath("$.name").value("구글유저"))
                .andExpect(jsonPath("$.role").value("TEAM_MEMBER"));
    }

    @Test
    @DisplayName("GET /auth/login/KAKAO - 카카오 로그인 성공")
    void kakaoLogin_success() throws Exception {
        String tokenJson = """
            {
              "access_token": "mock-kakao-access-token",
              "refresh_token": "mock-refresh-token",
              "id_token": "mock-id-token",
              "scope": "profile"
            }
            """;
        KakaoTokenResponse tokenResponse =
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(tokenJson, KakaoTokenResponse.class);

        String userInfoJson = """
            {
              "id": 98765,
              "kakao_account": {
                "profile": { "nickname": "카카오닉네임" }
              }
            }
            """;
        KakaoUserInfoResponse userInfo =
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(userInfoJson, KakaoUserInfoResponse.class);

        User mockUser = User.builder()
                .userId(UUID.randomUUID())
                .name("카카오닉네임")
                .role(Role.TEAM_MEMBER)
                .build();

        when(kakaoService.getAccessTokenFromKakao("test-code")).thenReturn(tokenResponse);
        when(kakaoService.getUserInfo("mock-kakao-access-token")).thenReturn(userInfo);
        when(userService.findOrCreateUser(any())).thenReturn(mockUser);
        when(jwtProvider.createToken(mockUser.getUserId(), mockUser.getRole()))
                .thenReturn("mock-jwt-token");

        mockMvc.perform(get("/auth/login/KAKAO")
                        .param("code", "test-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.name").value("카카오닉네임"));
    }

    @Test
    @DisplayName("GET /auth/login/GITHUB - 깃허브 로그인 성공")
    void githubLogin_success() throws Exception {
        String tokenJson = """
            {
              "access_token": "mock-github-access-token",
              "token_type": "bearer",
              "scope": "read:user"
            }
            """;
        GithubTokenResponse tokenResponse =
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(tokenJson, GithubTokenResponse.class);

        String userInfoJson = """
            {
              "id": 11111,
              "login": "ghuser",
              "name": "깃허브유저",
              "email": "gh@test.com",
              "avatar_url": "http://example.com/avatar.png"
            }
            """;
        GithubUserInfoResponse userInfo =
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(userInfoJson, GithubUserInfoResponse.class);

        User mockUser = User.builder()
                .userId(UUID.randomUUID())
                .name("깃허브유저")
                .email("gh@test.com")
                .role(Role.TEAM_MEMBER)
                .build();

        when(githubService.getAccessTokenFromGithub("test-code")).thenReturn(tokenResponse);
        when(githubService.getUserInfo("mock-github-access-token")).thenReturn(userInfo);
        when(userService.findOrCreateUser(any())).thenReturn(mockUser);
        when(jwtProvider.createToken(mockUser.getUserId(), mockUser.getRole()))
                .thenReturn("mock-jwt-token");

        mockMvc.perform(get("/auth/login/GITHUB")
                        .param("code", "test-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.name").value("깃허브유저"));
    }
}
