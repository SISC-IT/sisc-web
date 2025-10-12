package org.sejongisc.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoTokenResponse {

    // 역직렬화를 위해 JsonProperty를 사용
    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("access_token")   // 사용자 엑세스 토큰 값
    private String accessToken;

    @JsonProperty("id_token")   // ID 토큰 값
    private String idToken;

    @JsonProperty("expires_in") // 엑세스 토큰과 ID 토큰의 만료 시간(초)
    private String expiresIn;

    @JsonProperty("refresh_token")  // 사용자 리프레시 토큰 값
    private String refreshToken;

    @JsonProperty("refresh_token_expires_in")   // 리프레시 토큰 만료 시간(초)
    private String refreshTokenExpiresIn;

    @JsonProperty("scope")  // 인증된 사용자의 정보 조회 권한 범위
    private String scope;
}