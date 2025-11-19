package org.sejongisc.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetCommitRequest(

        @Schema(
                example = "b21c9f41-6f8b-4af7-bd42-93f2716c3142",
                description = "비밀번호 재설정을 위한 임시 토큰"
        )
        @NotBlank(message = "resetToken은 필수입니다.")
        String resetToken,


        @Schema(
                example = "Newpass123!",
                description = """
                새로운 비밀번호 입력
                - 8~20자
                - 대문자 최소 1개
                - 소문자 최소 1개
                - 숫자 최소 1개
                - 특수문자 최소 1개 (!@#$%^&*()_+=-{};:'",.<>/?)
            """
        )
        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-{}\\[\\];:'\",.<>/?]).{8,20}$",
                message = "비밀번호는 8~20자, 대소문자/숫자/특수문자를 모두 포함해야 합니다."
        )
        String newPassword
) { }
