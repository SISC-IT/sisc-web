package org.sejongisc.backend.auth.oauth;

import org.sejongisc.backend.auth.dto.KakaoUserInfoResponse;
import org.sejongisc.backend.auth.entity.AuthProvider;

import java.util.Optional;

public class KakaoUserInfoAdapter implements OauthUserInfo {

    private final KakaoUserInfoResponse kakaoInfo;

    public KakaoUserInfoAdapter(KakaoUserInfoResponse kakaoInfo) {
        this.kakaoInfo = kakaoInfo;
    }

    @Override
    public String getProviderUid() {
        return String.valueOf(kakaoInfo.getId());
    }

    @Override
    public String getName() {
        return Optional.ofNullable(kakaoInfo.getKakaoAccount().getName())
                .orElse(kakaoInfo.getKakaoAccount().getProfile().getNickName());
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.KAKAO;
    }
}
