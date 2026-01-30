package org.sejongisc.backend.common.auth.entity;

public enum AuthProvider {
    GOOGLE,  // 구글
    GITHUB,  // 깃허브
    KAKAO;   // 카카오

    public static AuthProvider from(String providerName) {
        return switch (providerName.toLowerCase()) {
            case "google" -> GOOGLE;
            case "kakao" -> KAKAO;
            case "github" -> GITHUB;
            default -> throw new IllegalArgumentException("Unsupported provider: " + providerName);
        };
    }
}
