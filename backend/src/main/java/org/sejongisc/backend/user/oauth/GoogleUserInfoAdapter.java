package org.sejongisc.backend.user.oauth;

import org.sejongisc.backend.user.dto.GoogleUserInfoResponse;
import org.sejongisc.backend.user.entity.AuthProvider;

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
