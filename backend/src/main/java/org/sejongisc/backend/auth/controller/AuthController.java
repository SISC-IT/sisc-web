package org.sejongisc.backend.auth.controller;

import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    private final LoginService loginService;
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;


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
                                              "role": "TEAM_MEMBER",
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

        // accessToken을 HttpOnly 쿠키로 설정
        ResponseCookie accessCookie = ResponseCookie.from("access", response.getAccessToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(60L * 60)  // 1 hour
                .build();

        // refreshToken을 HttpOnly 쿠키로 설정
        ResponseCookie refreshCookie = ResponseCookie.from("refresh", response.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(60L * 60 * 24 * 14)  // 2 weeks
                .build();

        // JSON 응답에서 토큰 제거, 유저 정보만 포함
        LoginResponse safeResponse = LoginResponse.builder()
                .userId(response.getUserId())
                .email(response.getEmail())
                .name(response.getName())
                .role(response.getRole())
                .phoneNumber(response.getPhoneNumber())
                .point(response.getPoint())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(safeResponse);
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

        // 쿠키에 refreshToken이 없으면 401
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

            // accessToken을 HttpOnly 쿠키로 설정
            ResponseCookie accessCookie = ResponseCookie.from("access", tokens.get("accessToken"))
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("None")
                    .path("/")
                    .maxAge(60L * 60)  // 1 hour
                    .build();

            response.header(HttpHeaders.SET_COOKIE, accessCookie.toString());

            // JSON에서 accessToken 제거
            return response.body(Map.of("message", "토큰 갱신 성공"));

        } catch (Exception e) {
            log.warn("토큰 재발급 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Refresh Token이 유효하지 않거나 만료되었습니다."));
        }
    }

    @Operation(
            summary = "로그아웃 API",
            description = "Access Token을 무효화하고 Access/Refresh Token 쿠키를 삭제합니다. 토큰이 없어도 정상적으로 처리됩니다.",
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
                    )
            }
    )
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @Parameter(description = "Access Token 쿠키", example = "access=abc123")
            @CookieValue(value = "access", required = false) String accessToken,
            @Parameter(description = "Refresh Token 쿠키", example = "refresh=abc123")
            @CookieValue(value = "refresh", required = false) String refreshToken
    ) {
        // 토큰이 없어도 로그아웃 처리 (멱등성 보장)
        if (accessToken != null && !accessToken.isEmpty()) {
            try {
                loginService.logout(accessToken);
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("Invalid or expired JWT during logout: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error during logout", e);
            }
        }

        // Access Token 쿠키 삭제
        ResponseCookie deleteAccessCookie = ResponseCookie.from("access", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();

        // Refresh Token 쿠키 삭제
        ResponseCookie deleteRefreshCookie = ResponseCookie.from("refresh", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteAccessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, deleteRefreshCookie.toString())
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

