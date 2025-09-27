package org.sejongisc.backend.user.controller;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.user.dto.LoginRequest;
import org.sejongisc.backend.user.dto.LoginResponse;
import org.sejongisc.backend.user.service.LoginService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(loginService.login(request));
    }
}
