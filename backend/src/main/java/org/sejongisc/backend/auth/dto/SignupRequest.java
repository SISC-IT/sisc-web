package org.sejongisc.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.sejongisc.backend.user.entity.Role;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(
        name = "SignupRequest",
        description = "회원가입 요청 객체 (이름, 이메일, 비밀번호, 역할, 전화번호 입력)"
)
public class SignupRequest {

    @Schema(
            description = "사용자 이름",
            example = "홍길동",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @Schema(
            description = "사용자 이메일 주소 (유효한 이메일 형식이어야 함)",
            example = "testuser@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "이메일은 필수입니다.")
    @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9+_.'-]*[A-Za-z0-9]@[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$",
            message = "유효한 이메일 형식이 아닙니다."
    )
    private String email;

    @Schema(
            description = "사용자 비밀번호 (8자 이상, 숫자/영문/특수문자 조합 권장)",
            example = "Abcd1234!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    @Schema(
            description = "사용자 역할 (USER 또는 ADMIN 등)",
            example = "TEAM_MEMBER",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "역할은 필수입니다.")
    private Role role;

    @Schema(
            description = "전화번호 (숫자만 입력, 10~11자리)",
            example = "01012345678",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(
            regexp = "^[0-9]{10,11}$",
            message = "전화번호는 10~11자리 숫자여야 합니다."
    )
    private String phoneNumber;
}
