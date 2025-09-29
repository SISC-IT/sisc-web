package org.sejongisc.backend.user.service;

import org.sejongisc.backend.user.dto.KakaoUserInfoResponse;

public interface KakaoService {
    String getAccessTokenFromKakao(String code);
    KakaoUserInfoResponse getUserInfo(String accessToken);
}
