package org.sejongisc.backend.auth.service;

public interface Oauth2Service<TToken, TUserInfo> {

    // TToken → 플랫폼별 토큰 DTO (KakaoTokenResponse, GoogleTokenResponse, GithubTokenResponse)
    // TUserInfo → 플랫폼별 유저 정보 DTO (KakaoUserInfoResponse, GoogleUserInfoResponse, GithubUserInfoResponse)

    TToken getAccessToken(String code);
    TUserInfo getUserInfo(String accessToken);
}