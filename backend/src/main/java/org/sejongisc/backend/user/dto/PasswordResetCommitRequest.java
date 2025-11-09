package org.sejongisc.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetCommitRequest(
        @NotBlank(message = "resetToken은 필수입니다.")
        String resetToken,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-{}\\[\\];:'\",.<>/?]).{8,20}$",
                message = "비밀번호는 8~20자, 대소문자/숫자/특수문자를 모두 포함해야 합니다."
        )
        String newPassword
) { }
