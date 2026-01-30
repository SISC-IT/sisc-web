package org.sejongisc.backend.common.auth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.entity.RefreshToken;
import org.sejongisc.backend.common.auth.repository.RefreshTokenRepository;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.auth.jwt.TokenEncryptor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private final TokenEncryptor tokenEncryptor;

    @Override
    @Transactional
    public Map<String, String> reissueTokens(String encryptedRefreshToken) {
        try {
            // 전달받은 refreshToken 복호화
            String rawRefreshToken = tokenEncryptor.decrypt(encryptedRefreshToken);

            // refreshToken에서 userId 추출
            UUID userId = UUID.fromString(jwtProvider.getUserIdFromToken(rawRefreshToken));

            // DB에서 저장된 refreshToken 확인
            RefreshToken saved = refreshTokenRepository.findByUserId(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

            String savedRawToken = tokenEncryptor.decrypt(saved.getToken());
            if (!MessageDigest.isEqual(
                    rawRefreshToken.getBytes(StandardCharsets.UTF_8),
                    savedRawToken.getBytes(StandardCharsets.UTF_8))) {
                throw new CustomException(ErrorCode.UNAUTHORIZED);
            }

            // User 조회
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            // 새 Access Token 발급
            String newAccessToken = jwtProvider.createToken(
                    user.getUserId(), user.getRole(), user.getEmail());

            // Refresh Token 만료 임박 시 새로 발급
            Date expiration = jwtProvider.getExpiration(rawRefreshToken);
            long remainingMillis = expiration.getTime() - System.currentTimeMillis();
            String newRefreshToken = null;

            // 예: 남은 기간이 3일 미만이면 refreshToken도 갱신
            if (remainingMillis < (3L * 24 * 60 * 60 * 1000)) {
                newRefreshToken = jwtProvider.createRefreshToken(user.getUserId());
                saved.setToken(newRefreshToken);
                refreshTokenRepository.save(saved);
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

    @Override
    @Transactional
    public void saveOrUpdateToken(UUID userId, String refreshToken) {
        refreshTokenRepository.findByUserId(userId)
                .ifPresentOrElse(
                        existing -> existing.setToken(refreshToken),
                        () -> refreshTokenRepository.save(new RefreshToken(userId, refreshToken))
                );
        log.info("RefreshToken 저장 또는 갱신 완료: userId={}", userId);
    }

}
