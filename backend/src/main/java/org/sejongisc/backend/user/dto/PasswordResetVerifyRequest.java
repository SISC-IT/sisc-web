package org.sejongisc.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetVerifyRequest(

        @Schema(
                example = "testuser@example.com",
                description = "사용자의 이메일 주소"
        )
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @Schema(
                example = "482915",
                description = "이메일로 발송된 6자리 인증 코드"
        )
        @NotBlank(message = "인증코드는 필수입니다.")
        @Size(min = 6, max = 6, message = "인증코드는 6자리여야 합니다.")
        String code
) {}
