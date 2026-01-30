package org.sejongisc.backend.common.auth.dto.oauth;

import org.sejongisc.backend.common.auth.entity.AuthProvider;

import java.util.Optional;

public class GoogleUserInfoAdapter implements OauthUserInfo{

    private final GoogleUserInfoResponse googleInfo;
    private final String accessToken;

    public GoogleUserInfoAdapter(GoogleUserInfoResponse googleInfo, String accessToken) {
        this.googleInfo = googleInfo;
        this.accessToken = accessToken;
    }

    @Override
    public String getProviderUid() {
        return googleInfo.getSub();
    }

    @Override
    public String getName() {
        return Optional.ofNullable(googleInfo.getName())
                .orElse("googleUser");
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }
}
