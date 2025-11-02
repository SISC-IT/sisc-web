package org.sejongisc.backend.auth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.entity.RefreshToken;
import org.sejongisc.backend.auth.repository.RefreshTokenRepository;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Override
    public Map<String, String> reissueTokens(String refreshToken) {
        try {
            // refreshToken에서 userId 추출
            UUID userId = UUID.fromString(jwtProvider.getUserIdFromToken(refreshToken));

            // DB에서 저장된 refreshToken 확인
            RefreshToken savedRefreshToken = refreshTokenRepository.findByUserId(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

            if (!savedRefreshToken.getToken().equals(refreshToken)) {
                throw new CustomException(ErrorCode.UNAUTHORIZED);
            }

            // User 조회
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            // 새 Access Token 발급
            String newAccessToken = jwtProvider.createToken(
                    user.getUserId(), user.getRole(), user.getEmail());

            // Refresh Token 만료 임박 시 새로 발급
            Date expiration = jwtProvider.getExpiration(refreshToken);
            long remainingMillis = expiration.getTime() - System.currentTimeMillis();
            String newRefreshToken = null;

            // 예: 남은 기간이 3일 미만이면 refreshToken도 갱신
            if (remainingMillis < (3L * 24 * 60 * 60 * 1000)) {
                newRefreshToken = jwtProvider.createRefreshToken(user.getUserId());
                savedRefreshToken.setToken(newRefreshToken);
                refreshTokenRepository.save(savedRefreshToken);
                log.info("RefreshToken 재발급 완료: userId={}", userId);
            }

            // 결과 반환
            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", newAccessToken);
            if (newRefreshToken != null) tokens.put("refreshToken", newRefreshToken);

            log.info("AccessToken 재발급 완료: userId={}", userId);
            return tokens;

        } catch (CustomException e) {
            throw e; // 커스텀 예외는 그대로 던짐
        } catch (Exception e) {
            log.warn("AccessToken 재발급 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
        log.info("RefreshToken deleted for userId={}", userId);
    }

}
