package org.sejongisc.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(
        name = "UserUpdateRequest",
        description = "회원정보 수정 요청 객체 (이름, 전화번호, 비밀번호 중 수정할 항목만 입력)"
)
public class UserUpdateRequest {

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

    @Schema(
            description = "변경할 비밀번호 (선택 입력, 변경 시에만 포함)",
            example = "newpassword123!"
    )
    @Size(min = 8, message = "비밀번호는 최소 8자 이상 입력해야 합니다.")
    private String password;
}
