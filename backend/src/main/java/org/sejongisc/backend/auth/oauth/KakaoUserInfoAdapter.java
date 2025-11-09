package org.sejongisc.backend.auth.oauth;

import org.sejongisc.backend.auth.dto.KakaoUserInfoResponse;
import org.sejongisc.backend.auth.entity.AuthProvider;

import java.util.Optional;

public class KakaoUserInfoAdapter implements OauthUserInfo {

    private final KakaoUserInfoResponse kakaoInfo;
    private final String accessToken;

    public KakaoUserInfoAdapter(KakaoUserInfoResponse kakaoInfo, String  accessToken) {
        this.kakaoInfo = kakaoInfo;
        this.accessToken = accessToken;
    }

    @Override
    public String getProviderUid() {
        return String.valueOf(kakaoInfo.getId());
    }

    @Override
    public String getName() {
        return Optional.ofNullable(kakaoInfo.getKakaoAccount())
                .flatMap(account -> Optional.ofNullable(account.getName())
                        .or(() -> Optional.ofNullable(account.getProfile())
                                .map(KakaoUserInfoResponse.KakaoAccount.Profile::getNickName)))
                .orElse("Unknown");
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }
}
