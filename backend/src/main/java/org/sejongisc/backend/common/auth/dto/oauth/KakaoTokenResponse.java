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
        name = "KakaoTokenResponse",
        description = "Kakao OAuth 로그인 후 토큰 발급 응답 객체"
)
public class KakaoTokenResponse {

    @Schema(
            description = "토큰 타입 (일반적으로 'bearer')",
            example = "bearer"
    )
    @JsonProperty("token_type")
    private String tokenType;

    @Schema(
            description = "카카오에서 발급한 Access Token (사용자 인증용)",
            example = "vNnM9Examp1eAcc3ssT0ken12345678"
    )
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(
            description = "OpenID Connect 인증 시 함께 발급되는 ID Token (JWT 형식)",
            example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjAifQ.eyJhdWQiOiI..."
    )
    @JsonProperty("id_token")
    private String idToken;

    @Schema(
            description = "Access Token 및 ID Token의 만료 시간(초 단위)",
            example = "21599"
    )
    @JsonProperty("expires_in")
    private String expiresIn;

    @Schema(
            description = "Access Token 재발급용 Refresh Token",
            example = "o9vF9Refr3shTok3nExample987654321"
    )
    @JsonProperty("refresh_token")
    private String refreshToken;

    @Schema(
            description = "Refresh Token의 만료 시간(초 단위)",
            example = "5183999"
    )
    @JsonProperty("refresh_token_expires_in")
    private String refreshTokenExpiresIn;

    @Schema(
            description = "인가 시 부여된 사용자 정보 접근 권한 범위 (공백으로 구분)",
            example = "account_email profile_image profile_nickname"
    )
    @JsonProperty("scope")
    private String scope;
}
