package org.sejongisc.backend.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.auth.dto.LoginRequest;
import org.sejongisc.backend.auth.dto.LoginResponse;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks private LoginServiceImpl loginService;

    @Test
    @DisplayName("정상 로그인 시 LoginResponse 반환")
    void login_success() {
        // given
        UUID userId = UUID.randomUUID();
        String rawPassword = "password123";
        String encodedPassword = "encodedPassword123";

        User user = User.builder()
                .userId(userId)
                .email("test@example.com")
                .name("홍길동")
                .passwordHash(encodedPassword)
                .role(Role.TEAM_MEMBER)
                .point(100)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword(rawPassword);

        given(userRepository.findUserByEmail("test@example.com"))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(true);
        given(jwtProvider.createToken(any(UUID.class), any(Role.class)))
                .willReturn("mocked-jwt-token");

        // when
        LoginResponse response = loginService.login(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mocked-jwt-token");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getRole()).isEqualTo(Role.TEAM_MEMBER);
        assertThat(response.getPoint()).isEqualTo(100);
    }

    @Test
    @DisplayName("이메일이 존재하지 않으면 USER_NOT_FOUND 예외 발생")
    void login_userNotFound() {
        // given
        LoginRequest request = new LoginRequest();
        request.setEmail("notfound@example.com");
        request.setPassword("password");

        given(userRepository.findUserByEmail("notfound@example.com"))
                .willReturn(Optional.empty());

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 UNAUTHORIZED 예외 발생")
    void login_wrongPassword() {
        // given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .userId(userId)
                .email("test@example.com")
                .name("홍길동")
                .passwordHash("encodedPassword123")
                .role(Role.TEAM_MEMBER)
                .point(50)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongPassword");

        given(userRepository.findUserByEmail("test@example.com"))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPassword", "encodedPassword123"))
                .willReturn(false);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
