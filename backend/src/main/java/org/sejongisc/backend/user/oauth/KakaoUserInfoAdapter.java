package org.sejongisc.backend.user.oauth;

import org.sejongisc.backend.user.dto.KakaoUserInfoResponse;
import org.sejongisc.backend.user.entity.AuthProvider;

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
