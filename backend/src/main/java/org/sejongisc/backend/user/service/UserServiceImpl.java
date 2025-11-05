package org.sejongisc.backend.user.service;


import org.sejongisc.backend.auth.service.OauthUnlinkService;
import org.sejongisc.backend.common.auth.jwt.TokenEncryptor;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.auth.dao.UserOauthAccountRepository;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.auth.dto.SignupRequest;
import org.sejongisc.backend.auth.dto.SignupResponse;
import org.sejongisc.backend.user.dto.UserUpdateRequest;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.auth.entity.UserOauthAccount;
import org.sejongisc.backend.auth.oauth.OauthUserInfo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserOauthAccountRepository oauthAccountRepository;
    private final OauthUnlinkService oauthUnlinkService;
    private final PasswordEncoder passwordEncoder;
    private final TokenEncryptor tokenEncryptor;



    @Override
    @Transactional
    public SignupResponse signUp(SignupRequest dto) {
        log.debug("[SIGNUP] request: {}", dto.getEmail());
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
    @Transactional
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
                            .role(Role.TEAM_MEMBER)
                            .build();

                    User savedUser = userRepository.save(newUser);

                    String encryptedToken = tokenEncryptor.encrypt(oauthInfo.getAccessToken());

                    UserOauthAccount newOauth = UserOauthAccount.builder()
                            .user(savedUser)
                            .provider(oauthInfo.getProvider())
                            .providerUid(providerUid)
                            .accessToken(encryptedToken)
                            .build();

                    oauthAccountRepository.save(newOauth);

                    return savedUser;
                });
    }

    @Override
    @Transactional
    public void updateUser(UUID userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이름 업데이트
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            user.setName(request.getName().trim());
        }

        // 전화번호 업데이트
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }

        if (request.getPassword() != null) {
            String trimmedPassword = request.getPassword().trim();
            if (trimmedPassword.isEmpty()) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            user.setPasswordHash(passwordEncoder.encode(trimmedPassword));
        }

        log.info("회원 정보가 수정되었습니다. userId={}", userId);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }


    @Override
    @Transactional
    public void deleteUserWithOauth(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Lazy 로딩 강제 초기화 (안정성 보강)
        user.getOauthAccounts().size();

        // 연동된 OAuth 계정이 있을 경우 모두 해제
        if (!user.getOauthAccounts().isEmpty()) {
            for (UserOauthAccount account : user.getOauthAccounts()) {
                String provider = account.getProvider().name();
                String providerUid = account.getProviderUid();
                String accessToken = tokenEncryptor.decrypt(account.getAccessToken());

                log.info("연결된 OAuth 계정 해제 중: provider={}, userId={}", provider, userId);

                // Kakao / Google / GitHub 연동 해제 서비스 연결
                switch (provider.toLowerCase()) {
                    case "kakao" -> oauthUnlinkService.unlinkKakao(accessToken);
                    case "google" -> oauthUnlinkService.unlinkGoogle(accessToken);
                    case "github" -> oauthUnlinkService.unlinkGithub(accessToken);
                    default -> log.warn("지원하지 않는 provider: {}", provider);
                }
            }
        }

        // Refresh Token (추후 구현 시 삭제)
        //refreshTokenRepository.deleteByUserId(userId);

        // User 삭제 (연관된 OAuthAccount는 Cascade로 자동 삭제)
        userRepository.delete(user);
        log.info("회원 탈퇴 완료: userId={}", userId);
    }


}
