package org.sejongisc.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(
        name = "UserUpdateRequest",
        description = "회원정보 수정 시, 이메일/비밀번호 중 수정할 항목만 입력"
)
public class UserUpdateRequest {
/*
    @Schema(
            description = "변경할 이름 (선택 입력)",
            example = "홍길동"
    )
    @Size(min = 1, max = 10, message = "이름은 1자 이상 10자 이하로 입력해주세요.")
    private String name;

    @Schema(
            description = "변경할 전화번호 (선택 입력, 숫자만 입력)",
            example = "01098765432"
    )
    @Pattern(
            regexp = "^[0-9]{10,11}$",
            message = "전화번호는 숫자만 10~11자리로 입력해주세요."
    )
    private String phoneNumber;
    */
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    @Schema(description = "비밀번호 재설정용 이메일", example = "sira@sejong.ac.kr")
    private String email;

    @Schema(
        description = "기존 비밀번호 (변경 시에만 포함)",
        example = "password123!"
    )
    @Size(min = 8, message = "비밀번호는 최소 8자 이상 입력해야 합니다.")
    private String currentPassword;

    @Schema(
            description = "변경할 비밀번호 (변경 시에만 포함)",
            example = "Newpassword123!"
    )
    @Size(min = 8, message = "비밀번호는 최소 8자 이상 입력해야 합니다.")
    private String newPassword;
}
