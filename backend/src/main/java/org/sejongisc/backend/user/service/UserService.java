package org.sejongisc.backend.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.activity.entity.ActivityLog;
import org.sejongisc.backend.activity.entity.ActivityType;
import org.sejongisc.backend.activity.repository.ActivityLogRepository;
import org.sejongisc.backend.common.auth.service.RefreshTokenService;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dto.UserUpdateRequest;
import org.sejongisc.backend.user.entity.Grade;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.entity.UserStatus;
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.user.util.PasswordPolicyValidator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final ActivityLogRepository activityLogRepository;

    // --- 핵심 회원 서비스 ---

    @Transactional
    public void updateUser(UUID userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().trim());
        }

        // 비밀번호 변경 로직 (새 비밀번호가 입력된 경우에만 실행)
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
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

    @Transactional(readOnly = true)
    public List<ActivityLog> getAttendanceActivityLog(UUID userId) {
        return activityLogRepository.findByUserIdAndActivityTypesOrderByCreatedAtDesc(userId,
                List.of(ActivityType.ATTENDANCE));
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getBoardActivityLog(UUID userId) {
        return activityLogRepository.findByUserIdAndActivityTypesOrderByCreatedAtDesc(userId,
                List.of(ActivityType.BOARD_LIKE, ActivityType.BOARD_POST, ActivityType.BOARD_COMMENT));
    }

    @Transactional
    public void deleteUserSoftDelete(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.setStatus(UserStatus.OUT);
        refreshTokenService.deleteByUserId(userId);
        log.info("회원 softdelete 처리 완료: userId={}", userId);
    }

    // --- Admin Only 메서드 ---

    @Transactional
    public void updateUserStatus(UUID userId, UserStatus status) {
        User user = findUser(userId);
        user.setStatus(status);
        log.info("사용자 상태 변경 완료: userId={}", userId);
    }

    @Transactional
    public void updateUserRole(UUID userId, Role role) {
        User user = findUser(userId);
        user.setRole(role);
        log.info("사용자 권한 변경 완료: userId={}", userId);
    }

    @Transactional
    public void promoteToSenior(UUID userId) {
        User user = findUser(userId);

        // grade 및 status 변경
        user.setGrade(Grade.SENIOR);
        user.setStatus(UserStatus.GRADUATED);

        log.info("선배 등급 전환 완료: userId={}, 학번={}", userId, user.getStudentId());
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<User> findAllUsersMissingAccount() {
        return userRepository.findAllUsersMissingAccount();
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