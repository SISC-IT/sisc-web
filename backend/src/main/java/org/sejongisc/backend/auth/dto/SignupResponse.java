package org.sejongisc.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Schema(
        name = "SignupResponse",
        description = "회원가입 성공 시 반환되는 응답 객체"
)
public class SignupResponse {

    @Schema(
            description = "사용자 고유 식별자(UUID)",
            example = "9f6d0e22-45f1-4e5e-bc94-f1f6e7d28b44"
    )
    private final UUID userId;

    @Schema(
            description = "사용자 이름",
            example = "홍길동"
    )
    private final String name;

    @Schema(
            description = "사용자 이메일 주소",
            example = "testuser@example.com"
    )
    private final String email;

    @Schema(
            description = "사용자 역할 (예: USER, ADMIN)",
            example = "USER"
    )
    private final Role role;

    @Schema(
            description = "계정 생성 시각 (Asia/Seoul 기준)",
            example = "2025-11-02T15:30:12"
    )
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private final LocalDateTime createdAt;

    @Schema(
            description = "계정 정보 마지막 수정 시각 (Asia/Seoul 기준)",
            example = "2025-11-02T15:30:12"
    )
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private final LocalDateTime updatedAt;

    private SignupResponse(UUID userId, String name, String email, Role role,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedDate(),
                user.getUpdatedDate()
        );
    }
}
