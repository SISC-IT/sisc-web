package org.sejongisc.backend.user.dto;

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
public class SignupRequestDto {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    @NotNull(message = "역할은 필수입니다.")
    private Role role;

    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^[0-9]{10,11}$")
    private String phoneNumber;
}
