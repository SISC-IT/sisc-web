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
        name = "GoogleTokenResponse",
        description = "Google OAuth 로그인 후 토큰 발급 응답 객체"
)
public class GoogleTokenResponse {

    @Schema(
            description = "Google에서 발급된 Access Token",
            example = "ya29.a0AfH6SMAF1Z8vF6c9lL7uN9LbQZxExampleToken"
    )
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(
            description = "Access Token의 만료 시간 (초 단위)",
            example = "3599"
    )
    @JsonProperty("expires_in")
    private Long expiresIn;

    @Schema(
            description = "새로운 Access Token을 발급받을 때 사용하는 Refresh Token",
            example = "1//0gkExampleRefreshToken"
    )
    @JsonProperty("refresh_token")
    private String refreshToken;

    @Schema(
            description = "OAuth 인증 범위 (space로 구분됨)",
            example = "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile openid"
    )
    @JsonProperty("scope")
    private String scope;

    @Schema(
            description = "OpenID Connect용 ID Token (JWT 형식)",
            example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjAifQ.eyJhenAiOiIx..."
    )
    @JsonProperty("id_token")
    private String idToken;

    @Schema(
            description = "토큰 타입 (일반적으로 'Bearer')",
            example = "Bearer"
    )
    @JsonProperty("token_type")
    private String tokenType;
}
