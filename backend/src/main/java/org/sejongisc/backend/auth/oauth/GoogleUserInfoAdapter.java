package org.sejongisc.backend.auth.oauth;

import org.sejongisc.backend.auth.dto.GoogleUserInfoResponse;
import org.sejongisc.backend.auth.entity.AuthProvider;

import java.util.Optional;

public class GoogleUserInfoAdapter implements OauthUserInfo{

    private final GoogleUserInfoResponse googleInfo;

    public GoogleUserInfoAdapter(GoogleUserInfoResponse googleInfo) {
        this.googleInfo = googleInfo;
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
}
