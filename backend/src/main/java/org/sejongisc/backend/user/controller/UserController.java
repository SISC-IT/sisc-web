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

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/details")
    public ResponseEntity<UserInfoResponse> getUserInfo(@AuthenticationPrincipal CustomUserDetails user) {
        log.info("email : " + user.getEmail() + " 권한: " + user.getAuthorities());

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
            @RequestBody UserUpdateRequest request
    ) {
        userService.updateUser(userId, request);
        return ResponseEntity.ok("회원 정보가 수정되었습니다.");
    }
}
