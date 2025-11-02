package org.sejongisc.backend.auth.service;

import java.util.Map;
import java.util.UUID;

public interface RefreshTokenService {

    /**
     * Refresh Token을 검증하고 새로운 Access Token을 재발급합니다.
     * Refresh Token의 만료가 임박하면 새 Refresh Token도 함께 반환합니다.
     *
     * @param refreshToken 클라이언트의 Refresh Token
     * @return Map {
     *     "accessToken": 새 Access Token,
     *     "refreshToken": (선택적) 새 Refresh Token
     * }
     */
    Map<String, String> reissueTokens(String refreshToken);
    void deleteByUserId(UUID userId);
    void saveOrUpdateToken(UUID userId, String refreshToken);
}
