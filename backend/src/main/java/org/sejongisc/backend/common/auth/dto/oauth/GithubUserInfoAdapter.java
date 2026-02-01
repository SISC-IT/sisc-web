package org.sejongisc.backend.common.auth.dto.oauth;

import org.sejongisc.backend.common.auth.entity.AuthProvider;

import java.util.Optional;

public class GithubUserInfoAdapter implements OauthUserInfo {

    private final GithubUserInfoResponse githubInfo;
    private final String accessToken;

    public GithubUserInfoAdapter(GithubUserInfoResponse githubInfo,  String accessToken) {
        this.githubInfo = githubInfo;
        this.accessToken = accessToken;
    }

    @Override
    public String getProviderUid() {
        return String.valueOf(githubInfo.getId());
    }

    @Override
    public String getName() {
        return Optional.ofNullable(githubInfo.getName())
                .orElseGet(() -> githubInfo.getLogin());    // login은 필수 필드이므로 fallback으로 사용
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GITHUB;
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }
}
