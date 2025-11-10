package org.sejongisc.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        name = "LoginRequest",
        description = "일반 로그인 요청 객체 (이메일과 비밀번호 입력)"
)
public class LoginRequest {

    @Schema(
            description = "사용자 이메일 주소",
            example = "testuser@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    private String email;

    @Schema(
            description = "사용자 비밀번호 (8자 이상, 특수문자 포함 권장)",
            example = "1234abcd!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private String password;
}
