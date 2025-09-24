package org.sejongisc.backend.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.user.dto.SignupRequestDto;
import org.sejongisc.backend.user.dto.SignupResponseDto;
import org.sejongisc.backend.user.dto.UserInfoResponseDto;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponseDto> signup(@Valid @RequestBody SignupRequestDto request) {
        SignupResponseDto response = userService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/details")
    public ResponseEntity<UserInfoResponseDto> getUserInfo(@AuthenticationPrincipal CustomUserDetails user) {
        log.info("email : " + user.getEmail() + " 권한: " + user.getAuthorities());

        UserInfoResponseDto response = new UserInfoResponseDto(
                user.getUuid(),
                user.getName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getPoint(),
                user.getRole().name(),
                user.getAuthorities()
        );

        return ResponseEntity.ok(response);
    }
}
