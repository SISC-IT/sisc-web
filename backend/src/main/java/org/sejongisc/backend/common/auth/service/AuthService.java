package org.sejongisc.backend.common.auth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.entity.RefreshToken;
import org.sejongisc.backend.common.auth.repository.RefreshTokenRepository;
import org.sejongisc.backend.common.auth.jwt.JwtParser;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.common.auth.dto.AuthRequest;
import org.sejongisc.backend.common.auth.dto.AuthResponse;
import org.sejongisc.backend.user.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtParser jwtParser;

    @Transactional
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByStudentId(request.getStudentId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 탈퇴 회원 로그인 차단
        if (!user.canLogin()) {
            throw new CustomException(ErrorCode.USER_WITHDRAWN);
        }

        // 비밀번호 일치 확인
        if (user.getPasswordHash() == null ||
            !passwordEncoder.matches(request.getPassword().trim(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = jwtProvider.createToken(user.getUserId(), user.getRole(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId());

        // 기존 토큰 삭제 후 새로 저장
        refreshTokenRepository.findByUserId(user.getUserId())
                .ifPresent(refreshTokenRepository::delete);

        refreshTokenRepository.save(
                RefreshToken.builder()
                        .userId(user.getUserId())
                        .token(refreshToken)
                        .build()
        );

        log.info("RefreshToken 저장 완료: userId={}", user.getUserId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .point(user.getPoint())
                .build();
    }

    @Transactional
    public void logout(String accessToken) {
        UUID userId = jwtParser.getUserIdFromToken(accessToken);
        refreshTokenRepository.deleteByUserId(userId);
        log.info("로그아웃 완료: userId={}", userId);
    }
}
