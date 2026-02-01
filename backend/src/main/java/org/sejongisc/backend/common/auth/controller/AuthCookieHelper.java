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
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();
    }

    private ResponseCookie createCookie(String name, String value, long maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                // 로컬에서
            .secure(false)
            .sameSite("Lax")
//                .secure(true)
//                .sameSite("None")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}