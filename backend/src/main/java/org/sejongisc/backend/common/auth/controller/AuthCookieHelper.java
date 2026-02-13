package org.sejongisc.backend.common.auth.controller;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieHelper {

    public ResponseCookie createAccessCookie(String token) {
        return createCookie("access", token, 60L * 60);
    }

    public ResponseCookie createRefreshCookie(String token) {
        return createCookie("refresh", token, 60L * 60 * 24 * 14);
    }

    public ResponseCookie deleteCookie(String name) {
        return createCookie(name, "", 0);
    }

    private ResponseCookie createCookie(String name, String value, long maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(false)      // 개발서버 설정 (http)
                .sameSite("Lax")
                //.secure(true)       // 배포서버 설정 (https)
                //.sameSite("None")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}