package org.sejongisc.backend.common.auth.dto.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
        name = "GoogleUserInfoResponse",
        description = "Google OAuth 로그인 후 사용자 정보 응답 객체"
)
public class GoogleUserInfoResponse {

    @Schema(
            description = "Google 사용자 고유 식별자 (sub)",
            example = "112233445566778899001"
    )
    @JsonProperty("sub")
    private String sub;

    @Schema(
            description = "Google 계정 이메일 주소",
            example = "johndoe@gmail.com"
    )
    @JsonProperty("email")
    private String email;

    @Schema(
            description = "Google 계정에 등록된 사용자 이름",
            example = "John Doe"
    )
    @JsonProperty("name")
    private String name;

    @Schema(
            description = "Google 프로필 사진 URL",
            example = "https://lh3.googleusercontent.com/a-/AOh14GgExamplePhotoURL"
    )
    @JsonProperty("picture")
    private String picture;
}
