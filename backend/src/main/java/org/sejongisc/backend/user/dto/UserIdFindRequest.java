package org.sejongisc.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserIdFindRequest(@NotBlank(message = "이름은 필수입니다.")
                                String name,

                                @NotBlank(message = "전화번호는 필수입니다.")
                                @Pattern(regexp = "^010\\d{8}$", message = "전화번호 형식이 올바르지 않습니다.")
                                String phoneNumber) {
}
