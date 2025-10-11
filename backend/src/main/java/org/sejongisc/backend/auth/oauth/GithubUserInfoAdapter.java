package org.sejongisc.backend.auth.oauth;

import org.sejongisc.backend.auth.dto.GithubUserInfoResponse;
import org.sejongisc.backend.auth.entity.AuthProvider;

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
        return githubInfo.getName();
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GITHUB;
    }
}
