package org.sejongisc.backend.auth.controller;

import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.dto.*;
import org.sejongisc.backend.auth.repository.RefreshTokenRepository;
import org.sejongisc.backend.auth.service.*;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.auth.oauth.GithubUserInfoAdapter;
import org.sejongisc.backend.auth.oauth.GoogleUserInfoAdapter;
import org.sejongisc.backend.auth.oauth.KakaoUserInfoAdapter;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(
        name = "인증 API",
        description = "회원 인증 및 소셜 로그인 관련 API를 제공합니다."
)
public class AuthController {

    private final Map<String, Oauth2Service<?, ?>> oauth2Services;
    private final LoginService loginService;
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final OauthStateService oauthStateService;
    private final RefreshTokenService refreshTokenService;

    @Value("${google.client.id}")
    private String googleClientId;

    @Value("${google.redirect.uri}")
    private String googleRedirectUri;

    @Value("${kakao.client.id}")
    private String kakaoClientId;

    @Value("${kakao.redirect.uri}")
    private String kakaoRedirectUri;

    @Value("${github.client.id}")
    private String githubClientId;

    @Value("${github.redirect.uri}")
    private String githubRedirectUri;


    @Operation(
            summary = "회원가입 API",
            description = """
                회원 이메일, 비밀번호, 이름, 전화번호 정보를 입력받아 새로운 사용자를 생성합니다.

                 비밀번호 정책:
                - 길이: 8~20자
                - 최소 1개의 대문자(A-Z)
                - 최소 1개의 소문자(a-z)
                - 최소 1개의 숫자(0-9)
                - 최소 1개의 특수문자(!@#$%^&*()_+=-{};:'",.<>/?)

                위 조건을 모두 만족하지 않으면 400 (INVALID_INPUT) 예외가 발생합니다.
                """,

            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "회원가입 성공",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "userId": "1c54b9f3-8234-4e8f-b001-11cc4d9012ab",
                                              "email": "testuser@example.com",
                                              "name": "홍길동",
                                              "phoneNumber": "01012345678",
                                              "role": "TEAM_MEMBER"
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "요청 데이터 유효성 검증 실패 (비밀번호 정책 미준수 포함)",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                        {
                                          "message": "비밀번호는 8~20자, 대소문자/숫자/특수문자를 모두 포함해야 합니다."
                                        }
                                        """))
                    )
            }
    )
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        log.info("[SIGNUP] request: {}", request.getEmail());
        SignupResponse response = userService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "일반 로그인 API",
            description = "이메일과 비밀번호로 로그인하고 Access Token과 Refresh Token을 발급합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "로그인 성공",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                                              "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
                                              "userId": "1c54b9f3-8234-4e8f-b001-11cc4d9012ab",
                                              "name": "홍길동",
                                              "role": "USER",
                                              "phoneNumber": "01012345678"
                                            }
                                            """))
                    ),
                    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
            }
    )
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {

        LoginResponse response = loginService.login(request);

        ResponseCookie cookie = ResponseCookie.from("refresh", response.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(60L * 60 * 24 * 14)
                .build();


        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + response.getAccessToken())
                .body(response);
    }

    // OAuth 로그인 시작 (state 생성 + 각 provider별 인증 URL 반환)
    @Operation(
            summary = "OAuth 로그인 시작 (INIT)",
            description = "소셜 로그인 시작 시 각 Provider(GOOGLE, KAKAO, GITHUB)의 인증 URL을 반환합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "OAuth 인증 URL 반환 성공",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = "\"https://accounts.google.com/o/oauth2/v2/auth?...\""))
                    ),
                    @ApiResponse(responseCode = "400", description = "지원하지 않는 Provider 요청")
            }
    )
    @GetMapping("/oauth/{provider}/init")
    public ResponseEntity<String> startOauthLogin(
            @Parameter(description = "소셜 로그인 제공자 (GOOGLE, KAKAO, GITHUB)", example = "GOOGLE")
            @PathVariable String provider,
            HttpSession session) {
        String state = oauthStateService.generateAndSaveState(session);
        String authUrl;

        switch (provider.toUpperCase()) {
            case "GOOGLE" -> authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=" + googleClientId +
                    "&redirect_uri=" + googleRedirectUri +
                    "&response_type=code" +
                    "&scope=email%20profile" +
                    "&state=" + state;
            case "KAKAO" -> authUrl = "https://kauth.kakao.com/oauth/authorize" +
                    "?client_id=" + kakaoClientId +
                    "&redirect_uri=" + kakaoRedirectUri +
                    "&response_type=code" +
                    "&state=" + state;
            case "GITHUB" -> authUrl = "https://github.com/login/oauth/authorize" +
                    "?client_id=" + githubClientId +
                    "&redirect_uri=" + githubRedirectUri +
                    "&scope=user:email" +
                    "&state=" + state;
            default -> throw new IllegalArgumentException("Unknown provider " + provider);
        }

        log.debug("Generated OAuth URL for {}: {}", provider, authUrl);
        return ResponseEntity.ok(authUrl);
    }

    //redirection api
    @Operation(
            summary = "OAuth 로그인 리다이렉트 (GET)",
            description = "소셜 로그인 후 리다이렉션 시 호출되는 엔드포인트입니다. "
                    + "code와 state 값을 받아 실제 로그인 과정을 처리하며 일반적으로 프론트엔드에서 이 요청을 자동으로 POST로 전달합니다."

    )
    @GetMapping("/login/{provider}")
    public ResponseEntity<?> handleOauthRedirect(
            @Parameter(description = "소셜 로그인 제공자", example = "GOOGLE") @PathVariable("provider") String provider,
            @Parameter(description = "OAuth 인증 코드", example = "4/0AbCdEfG...") @RequestParam("code") String code,
            @Parameter(description = "CSRF 방지용 state 값", example = "a1b2c3d4") @RequestParam("state") String state,
            HttpSession session) {
        log.info("[{}] OAuth GET redirect received: code={}, state={}", provider, code, state);

        // OAuth 로그인 처리
        ResponseEntity<LoginResponse> loginResponse = OauthLogin(provider, code, state, session);
        LoginResponse body = loginResponse.getBody();

        // 성공 시 프론트엔드 홈으로 리다이렉트 (accessToken, userId, name을 URL 파라미터로 전달)
        if (loginResponse.getStatusCode().is2xxSuccessful() && body != null) {
            String redirectUrl = "http://localhost:3000/?accessToken=" + body.getAccessToken()
                    + "&userId=" + body.getUserId()
                    + "&name=" + (body.getName() != null ? body.getName() : "");
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .header(HttpHeaders.SET_COOKIE, loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE) != null
                            ? loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE).get(0)
                            : "")
                    .build();
        }

        // 실패 시 로그인 페이지로 리다이렉트
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, "http://localhost:3000/login?error=oauth_failed")
                .build();
    }


    // OAuth 인증 완료 후 Code + State 처리
    @Operation(
            summary = "OAuth 로그인 완료 (POST)",
            description = "OAuth 인증 후 전달된 code와 state를 이용해 토큰을 발급받고 사용자 로그인 처리합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "OAuth 로그인 성공",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                                              "userId": "3a93f8c2-412b-4d9c-84a2-52bdfec91d11",
                                              "name": "카카오홍길동",
                                              "role": "USER",
                                              "phoneNumber": "01099998888"
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "잘못된 state 값 또는 만료된 인증 코드",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "Invalid OAuth state or expired authorization code"
                                            }
                                            """))
                    )
            }
    )
    @PostMapping("/login/{provider}")
    public ResponseEntity<LoginResponse> OauthLogin(
            @Parameter(description = "소셜 로그인 제공자", example = "KAKAO") @PathVariable("provider") String provider,
            @Parameter(description = "OAuth 인증 코드", example = "4/0AbCdEfG...") @RequestParam("code") String code,
            @Parameter(description = "CSRF 방지용 state 값", example = "a1b2c3d4") @RequestParam("state") String state,
            HttpSession session) {

        //  서버에 저장된 state와 요청으로 받은 state 비교
        String savedState = oauthStateService.getStateFromSession(session);

        if(savedState == null || !savedState.equals(state)) {
            log.warn("[{}] Invalid OAuth state detected. Expected={}, Received={}", provider, savedState, state);
            return ResponseEntity.status(401).build();
        }

        oauthStateService.clearState(session);

        Oauth2Service<?, ?> service = oauth2Services.get(provider.toUpperCase());
        if (service == null) {
            throw new IllegalArgumentException("Unknown provider " + provider);
        }

        User user = switch (provider.toUpperCase()) {
            case "GOOGLE" -> {
                var googleService = (Oauth2Service<GoogleTokenResponse, GoogleUserInfoResponse>) service;
                var token = googleService.getAccessToken(code);
                var info = googleService.getUserInfo(token.getAccessToken());
                yield userService.findOrCreateUser(new GoogleUserInfoAdapter(info, token.getAccessToken()));
            }
            case "KAKAO" -> {
                var kakaoService = (Oauth2Service<KakaoTokenResponse, KakaoUserInfoResponse>) service;
                var token = kakaoService.getAccessToken(code);
                var info = kakaoService.getUserInfo(token.getAccessToken());
                yield userService.findOrCreateUser(new KakaoUserInfoAdapter(info, token.getAccessToken()));
            }
            case "GITHUB" -> {
                var githubService = (Oauth2Service<GithubTokenResponse, GithubUserInfoResponse>) service;
                var token = githubService.getAccessToken(code);
                var info = githubService.getUserInfo(token.getAccessToken());
                yield userService.findOrCreateUser(new GithubUserInfoAdapter(info, token.getAccessToken()));
            }
            default -> throw new IllegalArgumentException("Unknown provider " + provider);
        };

        // Access 토큰 발급
        String accessToken = jwtProvider.createToken(user.getUserId(), user.getRole(), user.getEmail());

        String refreshToken = jwtProvider.createRefreshToken(user.getUserId());

        refreshTokenService.saveOrUpdateToken(user.getUserId(), refreshToken);

        // HttpOnly 쿠키에 담기
        ResponseCookie cookie = ResponseCookie.from("refresh", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(60L * 60 * 24 * 14) // 2주
                .build();

        // LoginResponse 생성
        LoginResponse response = LoginResponse.builder()
                .accessToken(accessToken)
                .userId(user.getUserId())
                .name(user.getName())
                .role(user.getRole())
                .phoneNumber(user.getPhoneNumber())
                .build();

        log.info("{} 로그인 성공: userId={}, provider={}", provider.toUpperCase(), user.getUserId(), provider.toUpperCase());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(response);
    }

    @Operation(
            summary = "Access Token 재발급 API",
            description = "만료된 Access Token을 Refresh Token으로 재발급받습니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Access Token 재발급 성공",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Refresh Token이 없거나 만료됨",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "Refresh Token이 유효하지 않거나 만료되었습니다."
                                            }
                                            """))
                    )
            }
    )
    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(
            @Parameter(description = "Refresh Token 쿠키", example = "refresh=abc123")
            @CookieValue(value = "refresh", required = false) String refreshToken
    ) {

        // ⃣ 쿠키에 refreshToken이 없으면 401
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Refresh Token이 없습니다."));
        }

        try {
            // 서비스 호출 → accessToken / refreshToken 갱신
            Map<String, String> tokens = refreshTokenService.reissueTokens(refreshToken);

            // accessToken을 Authorization 헤더로 전달
            ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("accessToken"));

            // refreshToken이 새로 발급된 경우 쿠키 교체
            if (tokens.containsKey("refreshToken")) {
                ResponseCookie cookie = ResponseCookie.from("refresh", tokens.get("refreshToken"))
                        .httpOnly(true)
                        .secure(true)  // Swagger/Postman 테스트 중일 땐 false
                        .sameSite("None")
                        .path("/")
                        .maxAge(60L * 60 * 24 * 14) // 2주
                        .build();

                response.header(HttpHeaders.SET_COOKIE, cookie.toString());
            }

            // 응답 반환
            return response.body(Map.of("accessToken", tokens.get("accessToken")));

        } catch (Exception e) {
            log.warn("토큰 재발급 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Refresh Token이 유효하지 않거나 만료되었습니다."));
        }
    }

    @Operation(
            summary = "로그아웃 API",
            description = "Access Token을 무효화하고 Refresh Token 쿠키를 삭제합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "로그아웃 성공",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "로그아웃 성공"
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Authorization 헤더 형식이 잘못됨",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "잘못된 Authorization 헤더 형식입니다."
                                            }
                                            """))
                    )
            }
    )
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @Parameter(description = "Bearer 토큰", example = "Bearer eyJhbGciOiJIUzI1NiJ9...")
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        log.info("📋 로그아웃 요청 도착");
        long startTime = System.currentTimeMillis();

        //  헤더 유효성 검사
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("❌ Authorization 헤더 형식 오류: 헤더가 없거나 'Bearer ' 형식이 아님");
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "잘못된 Authorization 헤더 형식입니다."));
        }

        String token = authorizationHeader.substring(7);
        log.info("🔐 토큰 추출 완료: 토큰 길이={}", token.length());

        // 예외 처리 및 멱등성 보장
        try {
            log.info("🔄 LoginService.logout() 호출 중...");
            loginService.logout(token);
            log.info("✅ LoginService.logout() 완료 - Refresh Token DB에서 삭제됨");
        } catch (JwtException | IllegalArgumentException e) {
            // 이미 만료되었거나 잘못된 토큰이라도 200 OK로 응답 (멱등성 보장)
            log.warn("⚠️ JWT 토큰 오류 (멱등성 보장으로 계속 진행): {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ 예상치 못한 오류 발생: {}", e.getMessage(), e);
            // 내부 예외는 500으로 보내지 않고 안전하게 처리
        }

        // Refresh Token 쿠키 삭제
        ResponseCookie deleteCookie = ResponseCookie.from("refresh", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();

        log.info("🍪 Refresh Token 쿠키 삭제 설정: maxAge=0, httpOnly=true, secure=true, sameSite=None");

        long endTime = System.currentTimeMillis();
        log.info("✅ 로그아웃 완료: 소요시간={}ms", (endTime - startTime));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(Map.of("message", "로그아웃 성공"));
    }

    @Operation(
            summary = "회원 탈퇴 API",
            description = "현재 로그인한 사용자의 계정을 삭제합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "회원 탈퇴 완료",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "회원 탈퇴가 완료되었습니다."
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "인증되지 않은 사용자",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "인증이 필요합니다."
                                            }
                                            """))
                    )
            }
    )
    @DeleteMapping("/withdraw")
    public ResponseEntity<?> withdraw(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "인증이 필요합니다."));
        }

        // DB에서 사용자 정보 삭제
        userService.deleteUserWithOauth(user.getUserId());
        log.info("회원 탈퇴 완료: {}", user.getEmail());

        //Refresh Token DB에서도 삭제
        refreshTokenService.deleteByUserId(user.getUserId());

        // 브라우저 쿠키 삭제
        ResponseCookie deleteCookie = ResponseCookie.from("refresh", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString()) // 나중에 추가
                .body(Map.of("message", "회원 탈퇴가 완료되었습니다."));
    }

}

