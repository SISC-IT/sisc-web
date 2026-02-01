package org.sejongisc.backend.common.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.sejongisc.backend.user.entity.Gender;
import org.sejongisc.backend.user.entity.Role;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "회원가입 요청 DTO")
public class SignupRequest {
    public static final String STUDENT_ID_REGEX = "^[0-9]{8}$"; // 8자리 학번
    public static final String PHONE_FORMAT_REGEX = "^010-\\d{3,4}-\\d{4}$";    // xxx-xxxx-xxxx 형식

    @NotBlank(message = "성함은 필수입니다.")
    @Schema(description = "성함", example = "홍길동")
    private String name;

    @NotBlank(message = "학번은 필수입니다.")
    @Pattern(regexp = STUDENT_ID_REGEX, message = "학번은 8자리 숫자여야 합니다.")
    @Schema(description = "학번 (로그인 ID로 사용)", example = "21010000")
    private String studentId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Schema(description = "비밀번호 (대소문자/숫자/특수문자 포함)", example = "Sira1234!")
    private String password;

    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = PHONE_FORMAT_REGEX, message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    @Schema(description = "전화번호", example = "010-1234-5678")
    private String phoneNumber;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    @Schema(description = "비밀번호 재설정용 이메일", example = "sira@sejong.ac.kr")
    private String email;

    @NotNull(message = "성별은 필수입니다.")
    @Schema(description = "성별", example = "MALE")
    private Gender gender;

    @Schema(description = "단과대학", example = "인공지능융합대학")
    private String college;

    @Schema(description = "학과", example = "컴퓨터공학과")
    private String department;

    @Schema(description = "기수", example = "25")
    private Integer generation;

    @Schema(description = "활동팀", example = "금융IT")
    private String teamName;

    @Schema(description = "기타 특이사항 (선배/운영부 등 가입 목적)", example = "10기 운영진 가입 신청입니다.")
    private String remark;
}
