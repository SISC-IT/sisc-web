package org.sejongisc.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class SignupResponse {
    private final UUID userId;
    private final String name;
    private final String email;
    private final Role role;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private final LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private final LocalDateTime updatedAt;

    private SignupResponse(UUID userId, String name, String email, Role role, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.userId=userId;
        this.name=name;
        this.email=email;
        this.role=role;
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