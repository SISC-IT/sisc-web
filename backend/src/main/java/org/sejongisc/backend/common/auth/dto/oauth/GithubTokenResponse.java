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
        name = "GithubTokenResponse",
        description = "GitHub OAuth 로그인 후 Access Token 응답 객체"
)
public class GithubTokenResponse {

    @Schema(
            description = "GitHub에서 발급된 Access Token",
            example = "gho_16a93b27fbc8e87a69c8aa0f3e7d7e7e8a2b"
    )
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(
            description = "토큰 타입 (일반적으로 'bearer')",
            example = "bearer"
    )
    @JsonProperty("token_type")
    private String tokenType;

    @Schema(
            description = "OAuth 인증 시 부여된 권한 범위 (scope)",
            example = "read:user,user:email"
    )
    @JsonProperty("scope")
    private String scope;
}
