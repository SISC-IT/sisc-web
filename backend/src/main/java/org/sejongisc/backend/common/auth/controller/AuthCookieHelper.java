package org.sejongisc.backend.common.auth.controller;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieHelper {

    private static final boolean SECURE_COOKIE = true;
    private static final String SAME_SITE = "Lax";

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
                .secure(SECURE_COOKIE)
                // Domain을 지정하지 않으면 host-only 쿠키가 되어 백엔드 API 도메인에만 묶인다
                // 현재 club/public/API 모두 기본 도메인 아래라 Lax로 충분하다
                // Vercel 등 완전히 다른 등록 도메인에서 프론트를 띄우면 cross-site fetch에 쿠키가 안 붙을 수 있다
                // 그때만 SameSite=None; Secure=true 정책으로 바꾸는 것을 검토한다
                .sameSite(SAME_SITE)
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
