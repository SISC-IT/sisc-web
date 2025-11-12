package org.sejongisc.backend.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.repository.RefreshTokenRepository;
import org.sejongisc.backend.common.auth.jwt.JwtParser;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.auth.dto.LoginRequest;
import org.sejongisc.backend.auth.dto.LoginResponse;
import org.sejongisc.backend.user.entity.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Ref;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtParser jwtParser;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findUserByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = jwtProvider.createToken(user.getUserId(), user.getRole(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId());

        // 기존 토큰 삭제 후 새로 저장
        refreshTokenRepository.findByUserId(user.getUserId())
                .ifPresent(refreshTokenRepository::delete);

        refreshTokenRepository.save(
                org.sejongisc.backend.auth.entity.RefreshToken.builder()
                        .userId(user.getUserId())
                        .token(refreshToken)
                        .build()
        );

        log.info("RefreshToken 저장 완료: userId={}", user.getUserId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .point(user.getPoint())
                .build();
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        log.info("🔄 [LoginServiceImpl.logout] 시작");
        try {
            log.info("🔐 JWT 토큰 파싱 중...");
            UUID userId = jwtParser.getUserIdFromToken(accessToken);
            log.info("✅ 토큰 파싱 완료: userId={}", userId);

            log.info("🗑️ Refresh Token 삭제 중: userId={}", userId);
            refreshTokenRepository.deleteByUserId(userId);
            log.info("✅ Refresh Token 삭제 완료: userId={}", userId);

            log.info("✅ [LoginServiceImpl.logout] 완료: userId={}", userId);
        } catch (Exception e) {
            log.error("❌ [LoginServiceImpl.logout] 오류 발생: {}", e.getMessage(), e);
            throw e;
        }
    }
}
