package org.sejongisc.backend.common.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.sejongisc.backend.user.entity.Role;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {

    @Schema(
            description = "Access Token (JWT 형식, API 요청 시 Authorization 헤더에 사용)",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1ZWM3OGYxMy04YzQyLTRjN2EtYmQyOS1hYWY5YmZkNzUxZDQiLCJleHAiOjE3Mjk3MzQ1OTF9.uqA0g_6PUjvksWJbZcY1E5z_1YHjeEd2oHg6jVbYHZQ"
    )
    private String accessToken;

    @Schema(
            description = "Refresh Token (JWT 형식, 쿠키 또는 재발급 요청에 사용)",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiaWF0IjoxNjg2MjMwMDAwfQ.sXk3kPn8n3g7H1uU1yXH0E8lJzGFXnNR9LkT6ZJfYfA"
    )
    private String refreshToken;

    @Schema(
            description = "사용자 고유 식별자(UUID)",
            example = "5ec78f13-8c42-4c7a-bd29-aaf9bfd751d4"
    )
    private UUID userId;

    @Schema(
            description = "사용자 이메일 주소",
            example = "testuser@example.com"
    )
    private String email;

    @Schema(
            description = "사용자 이름",
            example = "홍길동"
    )
    private String name;

    @Schema(
            description = "사용자 직위",
            example = "PRESIDENT"
    )
    private Role role;

    @Schema(
            description = "사용자 전화번호",
            example = "01012345678"
    )
    private String phoneNumber;

    @Schema(
            description = "현재 보유 포인트 (서비스 내 포인트 시스템에 따라 다름)",
            example = "1200"
    )
    private Integer point;
}
