package org.sejongisc.backend.common.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.dto.AuthRequest;
import org.sejongisc.backend.common.auth.dto.AuthResponse;
import org.sejongisc.backend.common.auth.service.AuthService;
import org.sejongisc.backend.common.auth.service.RefreshTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "01. 인증 API", description = "회원 인증 및 소셜 로그인 관련 API를 제공합니다.")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieHelper cookieHelper; // 주입

    @Operation(summary = "일반 로그인 API", description = "")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);

        ResponseCookie accessCookie = cookieHelper.createAccessCookie(response.getAccessToken());
        ResponseCookie refreshCookie = cookieHelper.createRefreshCookie(response.getRefreshToken());

        AuthResponse safeResponse = AuthResponse.builder()
            .userId(response.getUserId()).email(response.getEmail())
            .name(response.getName()).role(response.getRole())
            .phoneNumber(response.getPhoneNumber()).point(response.getPoint())
            .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
            .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
            .body(safeResponse);
    }

    @Operation(summary = "Access Token 재발급 API", description = "...")
    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(@CookieValue(value = "refresh", required = false) String refreshToken) {
        try {
            Map<String, String> tokens = refreshTokenService.reissueTokens(refreshToken);
            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok().header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("accessToken"));
            if (tokens.containsKey("refreshToken")) {
                responseBuilder.header(HttpHeaders.SET_COOKIE, cookieHelper.createRefreshCookie(tokens.get("refreshToken")).toString());
            }
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookieHelper.createAccessCookie(tokens.get("accessToken")).toString());
            return responseBuilder.body(Map.of("message", "토큰 갱신 성공"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Refresh Token이 유효하지 않거나 만료되었습니다."));
        }
    }

    @Operation(summary = "로그아웃 API", description = "...")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "access", required = false) String accessToken) {
        authService.logout(accessToken);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookieHelper.deleteCookie("access").toString())
            .header(HttpHeaders.SET_COOKIE, cookieHelper.deleteCookie("refresh").toString())
            .body(Map.of("message", "로그아웃 성공"));
    }
}