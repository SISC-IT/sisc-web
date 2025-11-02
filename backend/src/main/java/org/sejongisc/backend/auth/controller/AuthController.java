package org.sejongisc.backend.auth.controller;

import io.jsonwebtoken.JwtException;
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




    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        log.info("[SIGNUP] request: {}", request.getEmail());
        SignupResponse response = userService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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
    @GetMapping("/oauth/{provider}/init")
    public ResponseEntity<String> startOauthLogin(@PathVariable String provider, HttpSession session) {
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
    @GetMapping("/login/{provider}")
    public ResponseEntity<LoginResponse> handleOauthRedirect(
            @PathVariable("provider") String provider,
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpSession session) {
        log.info("[{}] OAuth GET redirect received: code={}, state={}", provider, code, state);
        return OauthLogin(provider, code, state, session);
    }


    // OAuth 인증 완료 후 Code + State 처리
    @PostMapping("/login/{provider}")
    public ResponseEntity<LoginResponse> OauthLogin(@PathVariable("provider") String provider, @RequestParam("code") String code, @RequestParam("state") String state, HttpSession session) {

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

    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(@CookieValue(value = "refresh", required = false) String refreshToken) {

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



    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        //  헤더 유효성 검사
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "잘못된 Authorization 헤더 형식입니다."));
        }

        String token = authorizationHeader.substring(7);

        // 예외 처리 및 멱등성 보장
        try {
            loginService.logout(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 이미 만료되었거나 잘못된 토큰이라도 200 OK로 응답 (멱등성 보장)
            log.warn("Invalid or expired JWT during logout: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during logout", e);
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

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(Map.of("message", "로그아웃 성공"));
    }

    @DeleteMapping("/withdraw")
    public ResponseEntity<?> withdraw(@AuthenticationPrincipal CustomUserDetails user) {
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
                // .header(HttpHeaders.SET_COOKIE, deleteCookie.toString()) // 나중에 추가
                .body(Map.of("message", "회원 탈퇴가 완료되었습니다."));
    }

}

