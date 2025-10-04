package org.sejongisc.backend.user.service;

import org.sejongisc.backend.user.dto.KakaoTokenResponse;
import org.sejongisc.backend.user.dto.KakaoUserInfoResponse;

public interface KakaoService {
    KakaoTokenResponse getAccessTokenFromKakao(String code);
    KakaoUserInfoResponse getUserInfo(String accessToken);
}
