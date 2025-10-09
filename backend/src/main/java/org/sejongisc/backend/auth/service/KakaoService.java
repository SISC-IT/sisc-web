package org.sejongisc.backend.auth.service;

import org.sejongisc.backend.auth.dto.KakaoTokenResponse;
import org.sejongisc.backend.auth.dto.KakaoUserInfoResponse;

public interface KakaoService {
    KakaoTokenResponse getAccessTokenFromKakao(String code);
    KakaoUserInfoResponse getUserInfo(String accessToken);
}
