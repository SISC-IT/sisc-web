package org.sejongisc.backend.auth.oauth;

import org.sejongisc.backend.auth.dto.GithubUserInfoResponse;
import org.sejongisc.backend.auth.entity.AuthProvider;

import java.util.Optional;

public class GithubUserInfoAdapter implements OauthUserInfo {

    private final GithubUserInfoResponse githubInfo;

    public GithubUserInfoAdapter(GithubUserInfoResponse githubInfo) {
        this.githubInfo = githubInfo;
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
}
