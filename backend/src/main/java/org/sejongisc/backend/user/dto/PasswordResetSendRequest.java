package org.sejongisc.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetSendRequest(

        @Schema(
                example = "testuser@example.com",
                description = "사용자의 이메일 주소"
        )
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,
        @NotBlank(message = "학번은 필수입니다.")
        String studentId
) { }
