package org.sejongisc.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private String name;

    @Schema(
            description = "변경할 전화번호 (선택 입력, 숫자만 입력)",
            example = "01098765432"
    )
    private String phoneNumber;

    @Schema(
            description = "변경할 비밀번호 (선택 입력, 변경 시에만 포함)",
            example = "newpassword123!"
    )
    private String password;
}
