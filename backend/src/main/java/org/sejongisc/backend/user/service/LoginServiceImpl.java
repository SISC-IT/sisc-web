package org.sejongisc.backend.user.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.dto.LoginRequest;
import org.sejongisc.backend.user.dto.LoginResponse;
import org.sejongisc.backend.user.entity.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService, UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findUserByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = jwtProvider.createToken(user.getUserId(), user.getRole());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .point(user.getPoint())
                .build();
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return new CustomUserDetails(user);
    }

}
