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

        String accessToken = jwtProvider.createToken(user.getUserId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId());

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
    public void logout(String accessToken) {
        UUID userId = jwtParser.getUserIdFromToken(accessToken);
        refreshTokenRepository.deleteByUserId(userId);
        log.info("로그아웃 완료: userId={}", userId);
    }
}
