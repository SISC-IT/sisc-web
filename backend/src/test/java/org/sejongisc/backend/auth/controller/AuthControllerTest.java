package org.sejongisc.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.auth.dto.*;
import org.sejongisc.backend.auth.dto.oauth.*;
import org.sejongisc.backend.auth.service.LoginService;
import org.sejongisc.backend.auth.service.oauth2.Oauth2Service;
import org.sejongisc.backend.auth.service.oauth2.OauthStateService;
import org.sejongisc.backend.auth.service.RefreshTokenService;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.exception.controller.GlobalExceptionHandler;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock Oauth2Service<GoogleTokenResponse, GoogleUserInfoResponse> googleService;
    @Mock Oauth2Service<KakaoTokenResponse, KakaoUserInfoResponse> kakaoService;
    @Mock Oauth2Service<GithubTokenResponse, GithubUserInfoResponse> githubService;

    @Mock LoginService loginService;
    @Mock UserService userService;
    @Mock JwtProvider jwtProvider;
    @Mock OauthStateService oauthStateService;
    @Mock RefreshTokenService refreshTokenService;

    @InjectMocks
    AuthController authController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Map<String, Oauth2Service<?, ?>> oauth2Services = Map.of(
                "GOOGLE", googleService,
                "KAKAO", kakaoService,
                "GITHUB", githubService
        );

        authController = new AuthController(
                oauth2Services,
                loginService,
                userService,
                jwtProvider,
                oauthStateService,
                refreshTokenService
        );

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new TestValidationHandler())
                .build();
    }

    @RestControllerAdvice
    static class TestValidationHandler {
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException ex) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "validation");
            body.put("message", "입력값 검증 실패");
            return ResponseEntity.badRequest().body(body); // 강제로 400 반환
        }
    }

    // 일반 로그인 성공
    @Test
    @DisplayName("POST /api/auth/login - 로그인 성공 시 200 OK")
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

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("mockAccessToken")))
                .andExpect(jsonPath("$.email", is("hong@example.com")))
                .andExpect(jsonPath("$.name", is("홍길동")))
                .andExpect(jsonPath("$.role", is("TEAM_MEMBER")))
                .andExpect(jsonPath("$.point", is(100)));
    }

    // 존재하지 않는 사용자
    @Test
    @DisplayName("POST /api/auth/login - 존재하지 않는 사용자면 404 반환")
    void login_userNotFound() throws Exception {
        when(loginService.login(any(LoginRequest.class)))
                .thenThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        LoginRequest req = new LoginRequest("notfound@example.com", "Password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("USER_NOT_FOUND")));
    }

    // 비밀번호 오류
    @Test
    @DisplayName("POST /api/auth/login - 비밀번호 틀리면 401 반환")
    void login_wrongPassword() throws Exception {
        when(loginService.login(any(LoginRequest.class)))
                .thenThrow(new CustomException(ErrorCode.UNAUTHORIZED));

        LoginRequest req = new LoginRequest("hong@example.com", "WrongPassword!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }

    // 구글 로그인 성공
    @Test
    @DisplayName("POST /api/auth/login/GOOGLE - 구글 로그인 성공")
    void googleLogin_success() throws Exception {
        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setAccessToken("mock-google-access-token");

        GoogleUserInfoResponse userInfo = new GoogleUserInfoResponse();
        userInfo.setEmail("google@test.com");
        userInfo.setName("구글유저");

        User user = User.builder()
                .userId(UUID.randomUUID())
                .name("구글유저")
                .email("google@test.com")
                .role(Role.TEAM_MEMBER)
                .build();

        when(oauthStateService.getStateFromSession(any())).thenReturn("test-state");
        when(googleService.getAccessToken("test-code")).thenReturn(tokenResponse);
        when(googleService.getUserInfo("mock-google-access-token")).thenReturn(userInfo);
        when(userService.findOrCreateUser(any())).thenReturn(user);
        when(jwtProvider.createToken(user.getUserId(), user.getRole(), user.getEmail()))
                .thenReturn("jwt-token");
        when(jwtProvider.createRefreshToken(user.getUserId())).thenReturn("refresh-token");

        mockMvc.perform(post("/api/auth/login/GOOGLE")
                        .param("code", "test-code")
                        .param("state", "test-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.name").value("구글유저"));
    }

    // OAuth state 불일치
    @Test
    @DisplayName("POST /api/auth/login/KAKAO - state 불일치 시 401 반환")
    void oauthLogin_invalidState() throws Exception {
        when(oauthStateService.getStateFromSession(any())).thenReturn("expectedState");

        mockMvc.perform(post("/api/auth/login/KAKAO")
                        .param("code", "test-code")
                        .param("state", "wrong-state"))
                .andExpect(status().isUnauthorized());
    }

    // 잘못된 provider
    @Test
    @DisplayName("POST /api/auth/login/unknown - 존재하지 않는 provider 요청 시 500")
    void oauthLogin_invalidProvider() throws Exception {
        when(oauthStateService.getStateFromSession(any())).thenReturn("test-state");

        mockMvc.perform(post("/api/auth/login/unknown")
                        .param("code", "code")
                        .param("state", "test-state"))
                .andExpect(status().is5xxServerError())
                .andExpect(result ->
                        assertTrue(result.getResolvedException() instanceof IllegalArgumentException));
    }

    // startOauthLogin - 모든 provider 테스트
    @Test
    @DisplayName("GET /api/auth/oauth/{provider}/init - OAuth URL 생성 확인")
    void startOauthLogin_allProviders() throws Exception {
        when(oauthStateService.generateAndSaveState(any(HttpSession.class))).thenReturn("state123");

        mockMvc.perform(get("/api/auth/oauth/google/init"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("accounts.google.com")));

        mockMvc.perform(get("/api/auth/oauth/kakao/init"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("kauth.kakao.com")));

        mockMvc.perform(get("/api/auth/oauth/github/init"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("github.com")));

        mockMvc.perform(get("/api/auth/oauth/unknown/init"))
                .andExpect(status().is5xxServerError());
    }

    // 로그아웃 정상
    @Test
    @DisplayName("POST /api/auth/logout - 정상 로그아웃 시 200 OK")
    void logout_success() throws Exception {
        String token = "fake.jwt.token";
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃 성공"));

        verify(loginService, times(1)).logout(token);
    }

    // Authorization 헤더 누락
    @Test
    @DisplayName("POST /api/auth/logout - Authorization 헤더 누락 시 400")
    void logout_missingHeader() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("잘못된 Authorization 헤더 형식입니다."));
        verify(loginService, never()).logout(anyString());
    }

    // 잘못된 토큰 (JwtException)
    @Test
    @DisplayName("POST /api/auth/logout - 잘못된 토큰이어도 200 OK 응답 (멱등성 보장)")
    void logout_invalidToken() throws Exception {
        doThrow(new JwtException("Invalid Token")).when(loginService).logout(anyString());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃 성공"));
    }

    // 회원가입 성공
    @Test
    @DisplayName("POST /api/auth/signup - 201 Created & 응답 DTO 반환")
    void signup_success() throws Exception {
        SignupRequest req = SignupRequest.builder()
                .name("홍길동")
                .email("hong@example.com")
                .password("Password123!")
                .role(Role.TEAM_MEMBER)
                .phoneNumber("01012345678")
                .build();

        UUID userId = UUID.randomUUID();
        User entity = User.builder()
                .userId(userId)
                .name("홍길동")
                .email("hong@example.com")
                .passwordHash("ENCODED")
                .role(Role.TEAM_MEMBER)
                .phoneNumber("01012345678")
                .build();

        SignupResponse resp = SignupResponse.from(entity);
        when(userService.signUp(any(SignupRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(userId.toString())))
                .andExpect(jsonPath("$.name", is("홍길동")));
    }

    // 회원가입 검증 실패
    @Test
    @DisplayName("POST /api/auth/signup - 요청 검증 실패 시 500 (GlobalExceptionHandler 미처리)")
    void signup_validation_fail() throws Exception {
        String invalidJson = """
            {
              "email":"hong@example.com",
              "password":"Password123!",
              "role":"TEAM_MEMBER"
            }
            """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isInternalServerError())
                .andExpect(result ->
                        assertTrue(result.getResolvedException() instanceof org.springframework.web.bind.MethodArgumentNotValidException));
    }

    @Test
    @DisplayName("POST /api/auth/login/KAKAO - 토큰 또는 유저정보 null일 때도 정상 처리")
    void kakaoLogin_partialCoverage() throws Exception {
        // given
        KakaoTokenResponse tokenResponse = new KakaoTokenResponse();
        tokenResponse.setAccessToken("mock-token");

        when(oauthStateService.getStateFromSession(any())).thenReturn("state123");
        when(kakaoService.getAccessToken(anyString())).thenReturn(tokenResponse);
        when(kakaoService.getUserInfo(anyString())).thenReturn(null); // info null

        when(userService.findOrCreateUser(any())).thenReturn(
                User.builder().userId(UUID.randomUUID()).name("NullInfoUser").role(Role.TEAM_MEMBER).build()
        );

        when(jwtProvider.createToken(any(), any(), any())).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(any())).thenReturn("refresh-token");

        mockMvc.perform(post("/api/auth/login/KAKAO")
                        .param("code", "dummy-code")
                        .param("state", "state123"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/auth/login/GITHUB - Github 토큰 정상이나 유저 정보 누락 시도 커버")
    void githubLogin_partialCoverage() throws Exception {
        GithubTokenResponse tokenResponse = new GithubTokenResponse();
        tokenResponse.setAccessToken("mock-gh-token");

        when(oauthStateService.getStateFromSession(any())).thenReturn("state123");
        when(githubService.getAccessToken(anyString())).thenReturn(tokenResponse);
        when(githubService.getUserInfo(anyString())).thenReturn(null); // info null

        when(userService.findOrCreateUser(any())).thenReturn(
                User.builder().userId(UUID.randomUUID()).name("GH-NullUser").role(Role.TEAM_MEMBER).build()
        );

        when(jwtProvider.createToken(any(), any(), any())).thenReturn("gh-token");
        when(jwtProvider.createRefreshToken(any())).thenReturn("gh-refresh");

        mockMvc.perform(post("/api/auth/login/GITHUB")
                        .param("code", "dummy-code")
                        .param("state", "state123"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/auth/login/UNKNOWN - Unknown provider 커버")
    void oauthLogin_unknownProvider_branch() throws Exception {
        when(oauthStateService.getStateFromSession(any())).thenReturn("state123");

        mockMvc.perform(post("/api/auth/login/UNDEFINED")
                        .param("code", "dummy")
                        .param("state", "state123"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("POST /api/auth/reissue - refreshToken 존재 시 AccessToken 재발급 성공")
    void reissue_success() throws Exception {
        String refreshToken = "valid.refresh.token";
        Map<String, String> tokens = Map.of("accessToken", "newAccessToken");

        when(refreshTokenService.reissueTokens(refreshToken)).thenReturn(tokens);

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(new jakarta.servlet.http.Cookie("refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("newAccessToken"));

        verify(refreshTokenService, times(1)).reissueTokens(refreshToken);
    }

    @Test
    @DisplayName("POST /api/auth/reissue - Refresh Token이 없으면 401 반환")
    void reissue_noToken() throws Exception {
        mockMvc.perform(post("/api/auth/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh Token이 없습니다."));

        verify(refreshTokenService, never()).reissueTokens(anyString());
    }

    @Test
    @DisplayName("POST /api/auth/reissue - Refresh Token이 유효하지 않으면 401 반환")
    void reissue_invalidToken() throws Exception {
        String invalidToken = "expired.refresh.token";

        when(refreshTokenService.reissueTokens(invalidToken))
                .thenThrow(new CustomException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(new jakarta.servlet.http.Cookie("refresh", invalidToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh Token이 유효하지 않거나 만료되었습니다."));
    }

    @Test
    @DisplayName("DELETE /api/auth/withdraw - 인증된 사용자가 회원 탈퇴 성공 시 200 OK")
    void withdraw_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        CustomUserDetails userDetails = new CustomUserDetails(
                User.builder()
                        .userId(userId)
                        .email("test@example.com")
                        .name("홍길동")
                        .role(Role.TEAM_MEMBER)
                        .build()
        );

        var auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        doNothing().when(userService).deleteUserWithOauth(userId);
        doNothing().when(refreshTokenService).deleteByUserId(userId);

        mockMvc.perform(delete("/api/auth/withdraw")
                        .requestAttr("user", userDetails)
                        .flashAttr("user", userDetails))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."));

        verify(userService, times(1)).deleteUserWithOauth(userId);
        verify(refreshTokenService, times(1)).deleteByUserId(userId);
    }

    @Test
    @DisplayName("DELETE /api/auth/withdraw - 인증되지 않은 사용자는 401 반환")
    void withdraw_unauthorized() throws Exception {
        mockMvc.perform(delete("/api/auth/withdraw"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

}
