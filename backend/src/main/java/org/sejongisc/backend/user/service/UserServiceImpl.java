package org.sejongisc.backend.user.service;


import org.sejongisc.backend.auth.service.EmailService;
import org.sejongisc.backend.auth.service.OauthUnlinkService;
import org.sejongisc.backend.auth.service.RefreshTokenService;
import org.sejongisc.backend.common.auth.jwt.TokenEncryptor;
import org.sejongisc.backend.user.util.PasswordPolicyValidator;
import org.springframework.data.redis.core.RedisTemplate;
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

import java.time.Duration;
import java.util.Optional;
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
    private final EmailService emailService;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RefreshTokenService refreshTokenService;


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

        // trim 적용 후 검증 및 저장
        String rawPassword = dto.getPassword();
        String trimmedPassword = rawPassword == null ? null : rawPassword.trim();

        // null / 공백 검사
        if (trimmedPassword == null || trimmedPassword.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // 비밀번호 정책 검증 (trim된 값으로)
        PasswordPolicyValidator.validate(trimmedPassword);

        // 패스워드 인코딩 (trim된 값 사용)
        String encodedPw = passwordEncoder.encode(trimmedPassword);

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

    @Override
    public String findEmailByNameAndPhone(String name, String phone){
        return userRepository.findByNameAndPhoneNumber(name, phone)
                .map(User::getEmail)
                .orElse(null);
    }

    @Override
    public void passwordReset(String email) {
        if (!userRepository.existsByEmail(email)) {
            log.debug("Password reset requested for non-existent email: {}", email);
            return;
        }
        // user가 있으면 email 전송
        emailService.sendResetEmail(email);
    }

    @Override
    public String verifyResetCodeAndIssueToken(String email, String code) {
        emailService.verifyResetEmail(email, code);
        String token = UUID.randomUUID().toString();

        try {
            redisTemplate.opsForValue().set("PASSWORD_RESET:" + token, email, Duration.ofMinutes(10));
        } catch (Exception e) {
            // Redis 장애 시 즉시 요청 자체를 실패로 처리
            log.error("Redis 연결 실패: 비밀번호 재설정 토큰 저장 불가", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return token;
    }

    @Override
    @Transactional
    public void resetPasswordByToken(String resetToken, String newPassword) {
//        String email = (String) redisTemplate.opsForValue().get("PASSWORD_RESET:" + resetToken);
        String email = null;

        try {
            email = (String) redisTemplate.opsForValue().get("PASSWORD_RESET:" + resetToken);
        } catch (Exception e) {
            log.error("Redis 연결 실패 - 비밀번호 재설정 토큰 조회 불가", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        if(email == null) {
            throw new CustomException(ErrorCode.EMAIL_CODE_NOT_FOUND);
        }

        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (newPassword == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String trimmedPassword = newPassword.trim();
        if (trimmedPassword.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // 반드시 trim된 값으로 정책 검증
        PasswordPolicyValidator.validate(trimmedPassword);

        // trim된 값을 인코딩하여 저장
        user.setPasswordHash(passwordEncoder.encode(trimmedPassword));
        userRepository.save(user);

        try {
            redisTemplate.delete("PASSWORD_RESET:" + resetToken);
        } catch (Exception e) {
            log.error("Redis 연결 실패 - 비밀번호 재설정 토큰 삭제 불가", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        refreshTokenService.deleteByUserId(user.getUserId());
    }

}
