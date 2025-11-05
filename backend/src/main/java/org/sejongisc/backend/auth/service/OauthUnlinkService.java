package org.sejongisc.backend.auth.service;

public interface OauthUnlinkService {

    /**
     * 카카오 계정 연동 해제
     * @param accessToken 사용자 카카오 액세스 토큰
     */
    void unlinkKakao(String accessToken);

    /**
     * 구글 계정 연동 해제
     * @param accessToken 사용자 구글 액세스 토큰
     */
    void unlinkGoogle(String accessToken);

    /**
     * 깃허브 계정 연동 해제
     * @param accessToken 사용자 깃허브 액세스 토큰
     */
    void unlinkGithub(String accessToken);
}
