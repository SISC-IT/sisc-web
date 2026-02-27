package org.sejongisc.backend.common.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.dto.AuthRequest;
import org.sejongisc.backend.common.auth.dto.AuthResponse;
import org.sejongisc.backend.common.auth.dto.SignupRequest;
import org.sejongisc.backend.common.auth.dto.SignupResponse;
import org.sejongisc.backend.common.auth.service.AuthService;
import org.sejongisc.backend.common.auth.service.RefreshTokenService;
import org.sejongisc.backend.user.dto.PasswordResetConfirmRequest;
import org.sejongisc.backend.user.dto.PasswordResetSendRequest;
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
    private final AuthCookieHelper cookieHelper;

    @Operation(summary = "회원 가입", description = "회장이 승인하기 전까지 PENDING 상태가 유지되며, 웹사이트를 사용할 수 없습니다.")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

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

    @Operation(
            summary = "비밀번호 재설정 : 이메일로 인증코드를 전송합니다.",
            description = """
        
        ## 인증(JWT): **불필요**

        ## 요청 바디 ( `PasswordResetSendRequest` )
        - **`email`**: 가입된 이메일
        - **`studentId`**: 가입된 학번
        
        ## 동작 설명
        - 입력한 이메일 + 학번으로 사용자를 확인합니다.
        - 일치하는 사용자가 있으면 인증코드를 생성합니다.
        - 인증코드를 Redis에 일정 시간 저장합니다. (TTL 적용)
        - 해당 이메일로 인증코드를 전송합니다.
        
        ## 반환값
        - 성공 메시지 (`인증코드를 전송했습니다.`)
        """)
    @PostMapping("/password/reset/send")
    public ResponseEntity<?> sendReset(@RequestBody @Valid PasswordResetSendRequest req){
        authService.passwordResetSendCode(req);
        return ResponseEntity.ok(Map.of("message", "인증코드를 전송했습니다."));
    }

    @Operation(
            summary = "비밀번호 재설정 : 인증코드와 새 비밀번호를 입력받아, 비밀번호를 변경합니다.",
            description = """
        
        ## 인증(JWT): **불필요**
        
        ## 요청 파라미터
        - **`code`**: 이메일로 받은 인증코드
        - **`newPassword`**: 새 비밀번호
        
        ## 요청 바디 ( `PasswordResetSendRequest` )
        - **`email`**: 가입된 이메일
        - **`studentId`**: 가입된 학번
        
        ## 동작 설명
        - 이메일 + 학번으로 사용자를 다시 확인합니다.
        - Redis에 저장된 인증코드와 입력한 `code`를 비교합니다.
        - 인증코드가 일치하면 새 비밀번호를 정책 검증 후 암호화하여 저장합니다.
        - 사용한 인증코드는 Redis에서 삭제합니다. (1회성)

        ## 반환값
        - 성공 메시지 (`비밀번호가 변경되었습니다.`)
        """)
    @PostMapping("/password/reset/confirm")
    public ResponseEntity<?> confirmReset(@RequestBody @Valid PasswordResetConfirmRequest req){
        authService.resetPasswordByCode(req);
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
    }
}