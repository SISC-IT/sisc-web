package org.sejongisc.backend.common.auth.dto;

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
public class AuthRequest {

    @Schema(
            description = "사용자 학번 (String)",
            example = "21010000",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "학번은 필수 입력값입니다.")
    private String studentId;

    @Schema(
            description = "사용자 비밀번호 (8자 이상, 특수문자 포함)",
            example = "Sira1234!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private String password;
}
