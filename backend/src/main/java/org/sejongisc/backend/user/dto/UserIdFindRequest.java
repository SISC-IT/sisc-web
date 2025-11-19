package org.sejongisc.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserIdFindRequest(
        @Schema(
                example = "홍길동",
                description = "수정할 사용자 이름"
        )
        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @Schema(
                example = "01098765432",
                description = "수정할 사용자 전화번호 (숫자만 입력)"
        )
        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^010\\d{8}$", message = "전화번호 형식이 올바르지 않습니다.")
        String phoneNumber) {
}
