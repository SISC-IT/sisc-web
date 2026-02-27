package org.sejongisc.backend.common.auth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.activity.entity.ActivityType;
import org.sejongisc.backend.activity.event.ActivityEvent;
import org.sejongisc.backend.common.annotation.OptimisticRetry;
import org.sejongisc.backend.common.auth.dto.SignupRequest;
import org.sejongisc.backend.common.auth.dto.SignupResponse;
import org.sejongisc.backend.common.auth.entity.RefreshToken;
import org.sejongisc.backend.common.auth.repository.RefreshTokenRepository;
import org.sejongisc.backend.common.auth.jwt.JwtParser;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.common.config.EmailProperties;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.redis.RedisKey;
import org.sejongisc.backend.common.redis.RedisService;
import org.sejongisc.backend.point.dto.AccountEntry;
import org.sejongisc.backend.point.entity.Account;
import org.sejongisc.backend.point.entity.AccountName;
import org.sejongisc.backend.point.entity.TransactionReason;
import org.sejongisc.backend.point.service.AccountService;
import org.sejongisc.backend.point.service.PointLedgerService;
import org.sejongisc.backend.user.dto.PasswordResetConfirmRequest;
import org.sejongisc.backend.user.dto.PasswordResetSendRequest;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.common.auth.dto.AuthRequest;
import org.sejongisc.backend.common.auth.dto.AuthResponse;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.util.PasswordPolicyValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final AccountService accountService;
    private final PointLedgerService pointLedgerService;
    private final ApplicationEventPublisher eventPublisher;

    private final EmailService emailService;
    private final RedisTemplate<String, String> redisTemplate;
    private final EmailProperties emailProperties;
    private final RedisService redisService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    @OptimisticRetry
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByStudentId(request.getStudentId())) {
            throw new CustomException(ErrorCode.DUPLICATE_USER);
        }

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
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
            eventPublisher.publishEvent(new ActivityEvent(
                    user.getUserId(),
                    user.getName(),
                    ActivityType.SIGNUP,
                    "일반 회원가입을 신청했습니다.",
                    null, null));
            return SignupResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_USER);
        }
    }

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

        if (user.getRole().equals(Role.PENDING_MEMBER)) {
            throw new CustomException(ErrorCode.NEED_PENDING_APPROVAL);
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
        eventPublisher.publishEvent(new ActivityEvent(
                user.getUserId(),
                user.getName(),
                ActivityType.AUTH_LOGIN,
                "로그인 했습니다.",
                null, null
        ));
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


    public void passwordResetSendCode(PasswordResetSendRequest req) {
        String nEmail = validateNotBlank(req.email(), "이메일").trim();
        String nStudentId = validateNotBlank(req.studentId(), "학번").trim();

        if (!userRepository.existsByEmailAndStudentId(nEmail, nStudentId)) {
            log.debug("이메일과 학번 불일치: email={}, studentId={}", nEmail, nStudentId);
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        emailService.sendResetEmail(nEmail);
    }

    @Transactional
    public void resetPasswordByCode(PasswordResetConfirmRequest req) {

        String email = validateNotBlank(req.email(), "이메일").trim();
        String studentId = validateNotBlank(req.studentId(), "학번").trim();
        String inputCode = validateNotBlank(req.code(), "인증코드").trim();

        // 사용자 조회 (이메일+학번 같이 확인하는 게 안전)
        User user = userRepository.findByEmailAndStudentId(email, studentId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Redis에서 인증코드 조회
        String redisKey = emailProperties.getKeyPrefix().getReset() + email;
        String savedCode = redisTemplate.opsForValue().get(redisKey);

        if (savedCode == null) {
            throw new CustomException(ErrorCode.RESET_CODE_EXPIRED);
        }

        if (!savedCode.equals(inputCode)) {
            throw new CustomException(ErrorCode.INVALID_RESET_CODE);
        }

        // 새 비밀번호 검증 + 암호화 저장
        String trimmedPassword = PasswordPolicyValidator.getValidatedPassword(req.newPassword());
        user.setPasswordHash(passwordEncoder.encode(trimmedPassword));

        // 인증코드 1회성 처리 (삭제)
        redisTemplate.delete(redisKey);

        // 기존 로그인 토큰 무효화
        refreshTokenService.deleteByUserId(user.getUserId());
    }


    private String validateNotBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            log.debug(fieldName);
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return value.trim();
    }

    private String getEmailFromRedis(String token) {
        try {
            String email = redisService.get(RedisKey.PASSWORD_RESET, token, String.class);
            if (email == null) throw new CustomException(ErrorCode.EMAIL_CODE_NOT_FOUND);
            return email;
        } catch (Exception e) {
            log.error("Redis 조회 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void deleteResetTokenFromRedis(String token) {
        try {
            redisService.delete(RedisKey.PASSWORD_RESET, token);
        } catch (Exception e) {
            log.error("Redis 삭제 실패", e);
        }
    }
}
