package org.sejongisc.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.auth.dto.*;
import org.sejongisc.backend.auth.service.LoginService;
import org.sejongisc.backend.auth.service.Oauth2Service;
import org.sejongisc.backend.auth.service.OauthStateService;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.exception.controller.GlobalExceptionHandler;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OauthLoginControllerTest {

    // 공통 OAuth 서비스
    @Mock Oauth2Service<GoogleTokenResponse, GoogleUserInfoResponse> googleService;
    @Mock Oauth2Service<KakaoTokenResponse, KakaoUserInfoResponse> kakaoService;
    @Mock Oauth2Service<GithubTokenResponse, GithubUserInfoResponse> githubService;

    @Mock LoginService loginService;
    @Mock UserService userService;
    @Mock JwtProvider jwtProvider;
    @Mock OauthStateService oauthStateService;
    @Mock HttpSession session;

    @InjectMocks
    OauthLoginController oauthLoginController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Map<String, Oauth2Service<?, ?>> oauth2Services = Map.of(
                "GOOGLE", googleService,
                "KAKAO", kakaoService,
                "GITHUB", githubService
        );

        oauthLoginController = new OauthLoginController(
                oauth2Services,
                loginService,
                userService,
                jwtProvider,
                oauthStateService
        );

        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders.standaloneSetup(oauthLoginController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ✅ 일반 로그인 테스트
    @Test
    @DisplayName("POST /auth/login - 로그인 성공 시 200 OK")
    void login_success() throws Exception {
        LoginRequest req = new LoginRequest("hong@example.com", "Password123!");
        LoginResponse resp = LoginResponse.builder()
                .accessToken("mockAccessToken")
                .refreshToken("mockRefreshToken")
                .userId(UUID.randomUUID())
                .email("hong@example.com")
                .name("홍길동")
                .role(Role.TEAM_MEMBER)
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

    // ✅ GOOGLE 로그인 테스트
    @Test
    @DisplayName("POST /auth/login/GOOGLE - 구글 로그인 성공")
    void googleLogin_success() throws Exception {
        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setAccessToken("mock-google-access-token");

        GoogleUserInfoResponse userInfo = new GoogleUserInfoResponse();
        userInfo.setEmail("google@test.com");
        userInfo.setName("구글유저");
        userInfo.setSub("12345");

        User mockUser = User.builder()
                .userId(UUID.randomUUID())
                .name("구글유저")
                .email("google@test.com")
                .role(Role.TEAM_MEMBER)
                .build();

        when(oauthStateService.getStateFromSession(any())).thenReturn("test-state");
        when(googleService.getAccessToken("test-code")).thenReturn(tokenResponse);
        when(googleService.getUserInfo("mock-google-access-token")).thenReturn(userInfo);
        when(userService.findOrCreateUser(any())).thenReturn(mockUser);
        when(jwtProvider.createToken(mockUser.getUserId(), mockUser.getRole())).thenReturn("mock-jwt-token");
        when(jwtProvider.createRefreshToken(mockUser.getUserId())).thenReturn("mock-refresh-token");

        mockMvc.perform(post("/auth/login/GOOGLE")
                        .param("code", "test-code")
                        .param("state", "test-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.name").value("구글유저"))
                .andExpect(jsonPath("$.role").value("TEAM_MEMBER"));
    }

    // ✅ KAKAO 로그인 테스트
    @Test
    @DisplayName("POST /auth/login/KAKAO - 카카오 로그인 성공")
    void kakaoLogin_success() throws Exception {
        KakaoTokenResponse tokenResponse = new KakaoTokenResponse();
        tokenResponse.setAccessToken("mock-kakao-access-token");

        KakaoUserInfoResponse userInfo = new KakaoUserInfoResponse();
        userInfo.setId(98765L);

        User mockUser = User.builder()
                .userId(UUID.randomUUID())
                .name("카카오닉네임")
                .role(Role.TEAM_MEMBER)
                .build();

        when(oauthStateService.getStateFromSession(any())).thenReturn("test-state");
        when(kakaoService.getAccessToken("test-code")).thenReturn(tokenResponse);
        when(kakaoService.getUserInfo("mock-kakao-access-token")).thenReturn(userInfo);
        when(userService.findOrCreateUser(any())).thenReturn(mockUser);
        when(jwtProvider.createToken(mockUser.getUserId(), mockUser.getRole())).thenReturn("mock-jwt-token");
        when(jwtProvider.createRefreshToken(mockUser.getUserId())).thenReturn("mock-refresh-token");

        mockMvc.perform(post("/auth/login/KAKAO")
                        .param("code", "test-code")
                        .param("state", "test-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.name").value("카카오닉네임"));
    }

    // ✅ GITHUB 로그인 테스트
    @Test
    @DisplayName("POST /auth/login/GITHUB - 깃허브 로그인 성공")
    void githubLogin_success() throws Exception {
        GithubTokenResponse tokenResponse = new GithubTokenResponse();
        tokenResponse.setAccessToken("mock-github-access-token");

        GithubUserInfoResponse userInfo = new GithubUserInfoResponse();
        userInfo.setName("깃허브유저");
        userInfo.setEmail("gh@test.com");

        User mockUser = User.builder()
                .userId(UUID.randomUUID())
                .name("깃허브유저")
                .email("gh@test.com")
                .role(Role.TEAM_MEMBER)
                .build();

        when(oauthStateService.getStateFromSession(any())).thenReturn("test-state");
        when(githubService.getAccessToken("test-code")).thenReturn(tokenResponse);
        when(githubService.getUserInfo("mock-github-access-token")).thenReturn(userInfo);
        when(userService.findOrCreateUser(any())).thenReturn(mockUser);
        when(jwtProvider.createToken(mockUser.getUserId(), mockUser.getRole())).thenReturn("mock-jwt-token");
        when(jwtProvider.createRefreshToken(mockUser.getUserId())).thenReturn("mock-refresh-token");

        mockMvc.perform(post("/auth/login/GITHUB")
                        .param("code", "test-code")
                        .param("state", "test-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.name").value("깃허브유저"));
    }
}
