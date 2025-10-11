package org.sejongisc.backend.user.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.auth.dao.UserOauthAccountRepository;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.auth.dto.SignupRequest;
import org.sejongisc.backend.auth.dto.SignupResponse;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.auth.entity.UserOauthAccount;
import org.sejongisc.backend.auth.oauth.OauthUserInfo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserOauthAccountRepository oauthAccountRepository;

    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public SignupResponse signUp(SignupRequest dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (userRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
            throw new CustomException(ErrorCode.DUPLICATE_PHONE);
        }

        // 패스워드 인코딩
        String encodedPw = passwordEncoder.encode(dto.getPassword());

        Role role = dto.getRole();
        if (role == null) {
            role = Role.TEAM_MEMBER;
        }

        User user = User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .passwordHash(encodedPw)
                .role(role)
                .phoneNumber(dto.getPhoneNumber())
                .build();

        try {
            User saved = userRepository.save(user);
            return SignupResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_USER);
        }

    }

    @Override
    public User findOrCreateUser(OauthUserInfo oauthInfo) {
        String providerUid = oauthInfo.getProviderUid();

        // 기존 OAuth 계정 찾기
        return oauthAccountRepository
                .findByProviderAndProviderUid(oauthInfo.getProvider(), providerUid)
                .map(UserOauthAccount::getUser)
                .orElseGet(() -> {
                    // 새로운 User 생성
                    User newUser = User.builder()
                            .name(oauthInfo.getName())
                            // .email(kakaoInfo.getKakaoAccount().getEmail()) // Email을 받기 위해서는 Kakao에 신청
                            .role(Role.TEAM_MEMBER)
                            .build();

                    User savedUser = userRepository.save(newUser);

                    UserOauthAccount newOauth = UserOauthAccount.builder()
                            .user(savedUser)
                            .provider(oauthInfo.getProvider())
                            .providerUid(providerUid)
                            .build();

                    oauthAccountRepository.save(newOauth);

                    return savedUser;
                });
    }

}
