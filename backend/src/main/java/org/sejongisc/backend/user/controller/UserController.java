package org.sejongisc.backend.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.auth.dto.SignupRequest;
import org.sejongisc.backend.auth.dto.SignupResponse;
import org.sejongisc.backend.user.dto.UserInfoResponse;
import org.sejongisc.backend.user.dto.UserUpdateRequest;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/details")
    public ResponseEntity<?> getUserInfo(@AuthenticationPrincipal CustomUserDetails user) {
        if (user == null) {
            log.warn("인증되지 않은 사용자 접근 시도");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "인증이 필요합니다."));
        }

        log.info("email: {} 권한: {}", user.getUsername(), user.getAuthorities());

        UserInfoResponse response = new UserInfoResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getPoint(),
                user.getRole().name(),
                user.getAuthorities()
        );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<?> updateUser(
            @PathVariable UUID userId,
            @RequestBody @Valid UserUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails authenticatedUser
    ) {
//        if(authenticatedUser == null){
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "인증 정보가 필요합니다."));
//        }

        log.info("인증된 사용자 ID={}, 요청한 userId={}", authenticatedUser.getUserId(), userId);

        // 본인 허용
        if (!authenticatedUser.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "본인의 정보만 수정할 수 있습니다."));
        }

        userService.updateUser(userId, request);
        return ResponseEntity.ok("회원 정보가 수정되었습니다.");
    }
}
