package org.sejongisc.backend.auth.dto.oauth;

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
        name = "GithubUserInfoResponse",
        description = "GitHub OAuth 로그인 후 사용자 정보 응답 객체"
)
public class GithubUserInfoResponse {

    @Schema(
            description = "GitHub 사용자 고유 ID",
            example = "12345678"
    )
    @JsonProperty("id")
    private Long id;

    @Schema(
            description = "GitHub 사용자 로그인 아이디 (username)",
            example = "octocat"
    )
    @JsonProperty("login")
    private String login;

    @Schema(
            description = "GitHub 계정에 등록된 전체 이름",
            example = "The Octocat"
    )
    @JsonProperty("name")
    private String name;

    @Schema(
            description = "GitHub 계정에 등록된 이메일 주소",
            example = "octocat@github.com"
    )
    @JsonProperty("email")
    private String email;

    @Schema(
            description = "GitHub 프로필 이미지 URL",
            example = "https://avatars.githubusercontent.com/u/583231?v=4"
    )
    @JsonProperty("avatar_url")
    private String avatarUrl;
}
