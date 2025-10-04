package org.sejongisc.backend.user.oauth;

import org.sejongisc.backend.user.dto.GithubUserInfoResponse;
import org.sejongisc.backend.user.entity.AuthProvider;

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
