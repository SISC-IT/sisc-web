package org.sejongisc.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.common.auth.dto.LoginResponse;
import org.sejongisc.backend.user.entity.User;

@Getter
@AllArgsConstructor
@Schema(
        name = "UserInfoResponse",
        description = "사용자 정보 조회 응답 객체"
)
public class UserInfoResponse {

    @Schema(
            description = "사용자 고유 식별자 (UUID)",
            example = "9f6d0e22-45f1-4e5e-bc94-f1f6e7d28b44"
    )
    private UUID id;

    @Schema(
            description = "사용자 이름",
            example = "홍길동"
    )
    private String name;

    @Schema(
            description = "사용자 이메일 주소",
            example = "testuser@example.com"
    )
    private String email;

    @Schema(
            description = "전화번호 (하이픈 없이 숫자만)",
            example = "01012345678"
    )
    private String phoneNumber;

    @Schema(
            description = "사용자의 현재 포인트 (서비스 내 포인트 제도에 따라 다름)",
            example = "1200"
    )
    private Integer point;

    @Schema(
            description = "사용자 역할 (예: USER, ADMIN)",
            example = "TEAM_MEMBER"
    )
    private String role;  // enum Role을 String으로 변환

    @Schema(
            description = "부여된 권한 목록 (ROLE_USER, ROLE_ADMIN 등)",
            example = "[\"ROLE_USER\"]"
    )
    private Collection<?> authorities;

  public static UserInfoResponse from(CustomUserDetails user) {
    if (user == null) {
      return null;
    }

    return new UserInfoResponse(
        user.getUserId(),
        user.getName(),
        user.getEmail(),
        user.getPhoneNumber(),
        user.getPoint(),
        user.getRole().name(),
        user.getAuthorities()
    );
  }

  public static UserInfoResponse from(User user) {
    if (user == null) {
      return null;
    }

    CustomUserDetails userDetails = new CustomUserDetails(user);

    return from(userDetails);
  }
}
