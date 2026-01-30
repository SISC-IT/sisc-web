package org.sejongisc.backend.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.dto.SignupRequest;
import org.sejongisc.backend.common.auth.dto.SignupResponse;
import org.sejongisc.backend.common.auth.service.EmailService;
import org.sejongisc.backend.common.auth.service.RefreshTokenService;
import org.sejongisc.backend.common.annotation.OptimisticRetry;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.point.dto.AccountEntry;
import org.sejongisc.backend.point.entity.Account;
import org.sejongisc.backend.point.entity.AccountName;
import org.sejongisc.backend.point.entity.TransactionReason;
import org.sejongisc.backend.point.service.AccountService;
import org.sejongisc.backend.point.service.PointLedgerService;
import org.sejongisc.backend.user.dto.UserUpdateRequest;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.entity.UserStatus;
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.user.util.PasswordPolicyValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RefreshTokenService refreshTokenService;
    private final AccountService accountService;
    private final PointLedgerService pointLedgerService;

    // --- 핵심 회원 서비스 ---

    @Transactional
    @OptimisticRetry
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmailOrStudentId(request.getEmail(), request.getStudentId())) {
            if (userRepository.existsByStudentId(request.getStudentId())) throw new CustomException(ErrorCode.DUPLICATE_USER);
            throw new CustomException(ErrorCode.DUPLICATE_PHONE);
        }
        String trimmedPassword = PasswordPolicyValidator.getValidatedPassword(request.getPassword());
        String encodedPw = passwordEncoder.encode(trimmedPassword);
        User user = User.createUserWithSignupAndPending(request, encodedPw);

        try {
            User saved = userRepository.save(user);
            Account userAccount = accountService.createUserAccount(user.getUserId());
            pointLedgerService.processTransaction(
                TransactionReason.SIGNUP_REWARD,
                user.getUserId(),
                AccountEntry.credit(accountService.getAccountByName(AccountName.SYSTEM_ISSUANCE), 100L),
                AccountEntry.debit(userAccount, 100L)
            );
            log.info("포인트 계정 생성 및 초기 포인트 지급 완료: {}", user.getEmail());
            return SignupResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_USER);
        }
    }

    @Transactional
    public void updateUser(UUID userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().trim());
        }

        // 비밀번호 변경 로직 (새 비밀번호가 입력된 경우에만 실행)
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (!passwordEncoder.matches(request.getNewPassword(), request.getNewPassword())) {
                throw new CustomException(ErrorCode.INVALID_INPUT); // 비밀번호 불일치 에러
            }
            // 새 비밀번호 정제 및 정책 검증
            String cleanNewPassword = PasswordPolicyValidator.getValidatedPassword(request.getNewPassword());

            // 새 비밀번호 인코딩 및 설정
            user.setPasswordHash(passwordEncoder.encode(cleanNewPassword));

            // 비밀번호 변경 시 모든 기기 로그아웃 처리 (선택 사항)
            refreshTokenService.deleteByUserId(user.getUserId());
        }
        log.info("회원 정보 수정 완료: userId={}", userId);
    }

    public void passwordReset(String email) {
        String nEmail = validateNotBlank(email, "이메일");

        if (!userRepository.existsByEmail(nEmail)) {
            log.debug("존재하지 않는 이메일로 비밀번호 재설정 요청: {}", nEmail);
            return;
        }

        emailService.sendResetEmail(nEmail);
    }

    public String verifyResetCodeAndIssueToken(String email, String code) {
        String nEmail = validateNotBlank(email, "이메일");
        String nCode = validateNotBlank(code, "인증코드");

        emailService.verifyResetEmail(nEmail, nCode);

        String token = UUID.randomUUID().toString();
        saveResetTokenToRedis(token, nEmail);

        return token;
    }

    @Transactional
    public void resetPasswordByToken(String resetToken, String newPassword) {
        String email = getEmailFromRedis(resetToken);
        User user = userRepository.findUserByEmail(email)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String trimmedPassword = PasswordPolicyValidator.getValidatedPassword(newPassword);

        user.setPasswordHash(passwordEncoder.encode(trimmedPassword));

        deleteResetTokenFromRedis(resetToken);
        refreshTokenService.deleteByUserId(user.getUserId());
    }

    public List<User> findAllUsersMissingAccount() {
        return userRepository.findAllUsersMissingAccount();
    }

    // --- 내부 헬퍼 메서드 ---

    private String validateNotBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return value.trim();
    }

    // TODO : RedisService로 분리 고려
    private void saveResetTokenToRedis(String token, String email) {
        try {
            redisTemplate.opsForValue().set("PASSWORD_RESET:" + token, email, Duration.ofMinutes(10));
        } catch (Exception e) {
            log.error("Redis 저장 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String getEmailFromRedis(String token) {
        try {
            String email = (String) redisTemplate.opsForValue().get("PASSWORD_RESET:" + token);
            if (email == null) throw new CustomException(ErrorCode.EMAIL_CODE_NOT_FOUND);
            return email;
        } catch (Exception e) {
            log.error("Redis 조회 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void deleteResetTokenFromRedis(String token) {
        try {
            redisTemplate.delete("PASSWORD_RESET:" + token);
        } catch (Exception e) {
            log.error("Redis 삭제 실패", e);
        }
    }

    public void deleteUserSoftDelete(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.setStatus(UserStatus.OUT);
        refreshTokenService.deleteByUserId(userId);
        log.info("회원 softdelete 처리 완료: userId={}", userId);
    }

    // ------------------------ (비활성화) OAuth2 관련 로직 ------------------------

    /*
    @Transactional
    public User upsertOAuthUser(String provider, String providerUid, String email, String name) {
        AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase());
        return oauthAccountRepository.findByProviderAndProviderUid(authProvider, providerUid)
            .map(UserOauthAccount::getUser)
            .orElseGet(() -> {
                User savedUser = userRepository.save(User.builder().email(email).name(name).role(Role.TEAM_MEMBER).build());
                oauthAccountRepository.save(UserOauthAccount.builder().user(savedUser).provider(authProvider).providerUid(providerUid).build());
                return savedUser;
            });
    }

    // 기존 findOrCreateUser는 upsertOAuthUser와 로직이 겹치므로 통합 권장하나, 유지 시 하단에 배치
    @Transactional
    @OptimisticRetry
    public User findOrCreateUser(OauthUserInfo oauthInfo) {
        return oauthAccountRepository.findByProviderAndProviderUid(oauthInfo.getProvider(), oauthInfo.getProviderUid())
            .map(UserOauthAccount::getUser)
            .orElseGet(() -> {
                User savedUser = userRepository.save(User.builder().name(oauthInfo.getName()).role(Role.TEAM_MEMBER).build());
                Account userAccount = accountService.createUserAccount(savedUser.getUserId());
                pointLedgerService.processTransaction(
                    TransactionReason.SIGNUP_REWARD,
                    savedUser.getUserId(),
                    AccountEntry.credit(accountService.getAccountByName(AccountName.SYSTEM_ISSUANCE), 100L),
                    AccountEntry.debit(userAccount, 100L)
                );
                log.info("포인트 계정 생성 및 초기 포인트 지급 완료: {}", savedUser.getEmail());
                oauthAccountRepository.save(UserOauthAccount.builder()
                    .user(savedUser).provider(oauthInfo.getProvider()).providerUid(oauthInfo.getProviderUid())
                    .accessToken(tokenEncryptor.encrypt(oauthInfo.getAccessToken())).build());
                return savedUser;
            });
    }

    @Transactional
    public void deleteUserWithOauth(UUID userId) {
        User user = findUserById(userId);
        user.getOauthAccounts().forEach(account -> {
            String provider = account.getProvider().name().toLowerCase();
            String accessToken = tokenEncryptor.decrypt(account.getAccessToken());
            switch (provider) {
                case "kakao" -> oauthUnlinkService.unlinkKakao(accessToken);
                case "google" -> oauthUnlinkService.unlinkGoogle(accessToken);
                case "github" -> oauthUnlinkService.unlinkGithub(accessToken);
                default -> log.warn("지원하지 않는 소셜 서비스: {}", provider);
            }
        });

        userRepository.delete(user);
        log.info("회원 탈퇴 완료: userId={}", userId);
    }
    */
}