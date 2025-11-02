package org.sejongisc.backend.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.auth.entity.RefreshToken;
import org.sejongisc.backend.auth.repository.RefreshTokenRepository;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private UUID userId;
    private String refreshToken;
    private User user;
    private RefreshToken savedToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        refreshToken = "dummy-refresh-token";

        user = User.builder()
                .userId(userId)
                .email("test@example.com")
                .role(Role.TEAM_MEMBER)
                .build();

        savedToken = RefreshToken.builder()
                .userId(userId)
                .token(refreshToken)
                .build();
    }

    @Test
    @DisplayName("정상 토큰 재발급 (RefreshToken 만료 여유 충분)")
    void reissueTokens_Success_NoRefreshRenewal() {
        // given
        when(jwtProvider.getUserIdFromToken(refreshToken)).thenReturn(userId.toString());
        when(refreshTokenRepository.findByUserId(userId)).thenReturn(Optional.of(savedToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtProvider.createToken(any(), any(), any())).thenReturn("new-access-token");

        // 만료 10일 남은 refresh token
        Date expiration = new Date(System.currentTimeMillis() + (10L * 24 * 60 * 60 * 1000));
        when(jwtProvider.getExpiration(refreshToken)).thenReturn(expiration);

        // when
        Map<String, String> result = refreshTokenService.reissueTokens(refreshToken);

        // then
        assertEquals("new-access-token", result.get("accessToken"));
        assertFalse(result.containsKey("refreshToken")); // refresh token은 재발급 안 됨
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("RefreshToken 남은 기간 3일 미만 → 새 RefreshToken도 재발급")
    void reissueTokens_Success_WithRefreshRenewal() {
        // given
        when(jwtProvider.getUserIdFromToken(refreshToken)).thenReturn(userId.toString());
        when(refreshTokenRepository.findByUserId(userId)).thenReturn(Optional.of(savedToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtProvider.createToken(any(), any(), any())).thenReturn("new-access-token");
        when(jwtProvider.createRefreshToken(userId)).thenReturn("new-refresh-token");

        // 만료 1일 남음
        Date expiration = new Date(System.currentTimeMillis() + (1L * 24 * 60 * 60 * 1000));
        when(jwtProvider.getExpiration(refreshToken)).thenReturn(expiration);

        // when
        Map<String, String> result = refreshTokenService.reissueTokens(refreshToken);

        // then
        assertEquals("new-access-token", result.get("accessToken"));
        assertEquals("new-refresh-token", result.get("refreshToken"));
        verify(refreshTokenRepository, times(1)).save(savedToken);
    }

    @Test
    @DisplayName("저장된 RefreshToken 불일치 → CustomException(UNAUTHORIZED)")
    void reissueTokens_TokenMismatch() {
        // given
        RefreshToken wrongToken = RefreshToken.builder()
                .userId(userId)
                .token("different-token")
                .build();

        when(jwtProvider.getUserIdFromToken(refreshToken)).thenReturn(userId.toString());
        when(refreshTokenRepository.findByUserId(userId)).thenReturn(Optional.of(wrongToken));

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> refreshTokenService.reissueTokens(refreshToken));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UserRepository에 사용자 없음 → USER_NOT_FOUND 예외 발생")
    void reissueTokens_UserNotFound() {
        // given
        when(jwtProvider.getUserIdFromToken(refreshToken)).thenReturn(userId.toString());
        when(refreshTokenRepository.findByUserId(userId)).thenReturn(Optional.of(savedToken));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> refreshTokenService.reissueTokens(refreshToken));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("DB에 RefreshToken 없음 → UNAUTHORIZED 예외 발생")
    void reissueTokens_RefreshNotFound() {
        // given
        when(jwtProvider.getUserIdFromToken(refreshToken)).thenReturn(userId.toString());
        when(refreshTokenRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> refreshTokenService.reissueTokens(refreshToken));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    @DisplayName("JwtProvider 내부 예외 → CustomException(UNAUTHORIZED) 반환")
    void reissueTokens_JwtException() {
        when(jwtProvider.getUserIdFromToken(refreshToken))
                .thenThrow(new RuntimeException("토큰 파싱 오류"));

        CustomException ex = assertThrows(CustomException.class,
                () -> refreshTokenService.reissueTokens(refreshToken));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    @DisplayName("✅ deleteByUserId 정상 동작")
    void deleteByUserId_Success() {
        doNothing().when(refreshTokenRepository).deleteByUserId(userId);

        refreshTokenService.deleteByUserId(userId);

        verify(refreshTokenRepository, times(1)).deleteByUserId(userId);
    }
}
