package org.sejongisc.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.common.auth.service.EmailService;
import org.sejongisc.backend.common.auth.service.RefreshTokenService;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.auth.repository.UserOauthAccountRepository;
import org.sejongisc.backend.common.redis.RedisKey;
import org.sejongisc.backend.common.redis.RedisService;
import org.sejongisc.backend.point.entity.Account;
import org.sejongisc.backend.point.service.*;
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.common.auth.dto.SignupRequest;
import org.sejongisc.backend.common.auth.dto.SignupResponse;
import org.sejongisc.backend.common.auth.entity.AuthProvider;
import org.sejongisc.backend.user.dto.UserUpdateRequest;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserOauthAccountRepository oauthAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private RedisService redisService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AccountService accountService;

    @Mock
    private PointLedgerService pointLedgerService;

    @InjectMocks private UserService userService;

    @Test
    @DisplayName("회원가입 성공: 비밀번호 인코딩, 저장, DTO 매핑 확인")
    void signup_success() {
        // given
        SignupRequest req = SignupRequest.builder()
                .name("홍길동")
                .email("hong@example.com")
                .password("Password123!")
                .phoneNumber("01012345678")
                .build();

        when(userRepository.existsByEmailOrStudentId(req.getEmail(), req.getStudentId())).thenReturn(false);

        when(accountService.createUserAccount(any())).thenReturn(Account.builder().build());
        when(accountService.getAccountByName(any())).thenReturn(Account.builder().build());

        when(passwordEncoder.encode(req.getPassword())).thenReturn("ENCODED_PW");

        UUID generatedId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // save 스텁: PK/감사필드 채워 반환 + 값 검증
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0, User.class);
            u.setUserId(generatedId);
            u.setCreatedDate(now);
            u.setUpdatedDate(now);
            assertThat(u.getPasswordHash()).isEqualTo("ENCODED_PW");
            assertThat(u.getRole()).isEqualTo(Role.TEAM_MEMBER);
            return u;
        });

        // when
        SignupResponse res = userService.signup(req);

        // then
        assertAll(
                () -> assertThat(res.getUserId()).isEqualTo(generatedId),
                () -> assertThat(res.getName()).isEqualTo("홍길동"),
                () -> assertThat(res.getEmail()).isEqualTo("hong@example.com"),
                () -> assertThat(res.getRole()).isEqualTo(Role.TEAM_MEMBER),
                // DTO가 createdDate/updatedDate를 노출한다는 전제
                () -> assertThat(res.getCreatedAt()).isEqualTo(now),
                () -> assertThat(res.getUpdatedAt()).isEqualTo(now)
        );

        verify(userRepository, times(1)).existsByEmailOrStudentId(req.getEmail(), req.getStudentId());
        verify(userRepository, times(1)).save(any(User.class));
        verify(passwordEncoder, times(1)).encode("Password123!");
    }

    @Test
    @DisplayName("회원가입 실패: 이메일 중복이면 CustomException(DUPLICATE_EMAIL)")
    void signup_duplicateEmail_throws() {
        // given
        SignupRequest req = SignupRequest.builder()
                .name("홍길동")
                .email("dup@example.com")
                .password("Password123!")
                //.role(Role.TEAM_MEMBER)
                .phoneNumber("01012345678")
                .build();

        when(userRepository.existsByEmailOrStudentId(req.getEmail(), req.getStudentId())).thenReturn(true);
        when(userRepository.existsByStudentId(req.getStudentId())).thenReturn(false);

        // when
        CustomException ex = assertThrows(CustomException.class, () -> userService.signup(req));

        // then
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        verify(userRepository, times(1)).existsByEmail("dup@example.com");
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }


    @Test
    @DisplayName("회원가입: Role이 null이면 기본값 MEMBER로 저장")
    void signup_nullRole_defaultsToMember() {
        // given
        SignupRequest req = SignupRequest.builder()
                .name("이몽룡")
                .email("lee@example.com")
                .password("Secret!234")
                .phoneNumber("01099998888")
                .build();

        when(userRepository.existsByEmailOrStudentId(any(), any())).thenReturn(false);

        when(accountService.createUserAccount(any())).thenReturn(Account.builder().build());
        when(accountService.getAccountByName(any())).thenReturn(Account.builder().build());

        when(passwordEncoder.encode(req.getPassword())).thenReturn("ENC_PW");

        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0, User.class);
            u.setUserId(id);
            u.setCreatedDate(now);
            u.setUpdatedDate(now);
            // 서비스에서 기본값을 TEAM_MEMBER로 세팅한다고 가정
            assertThat(u.getRole()).isEqualTo(Role.TEAM_MEMBER);
            return u;
        });

        // when
        SignupResponse res = userService.signup(req);

        // then
        assertThat(res.getRole()).isEqualTo(Role.TEAM_MEMBER);
    }


    @Test
    @DisplayName("회원가입 실패: 전화번호 중복이면 CustomException(DUPLICATE_PHONE)")
    void signup_duplicatePhone_throws() {
        // given
        SignupRequest req = SignupRequest.builder()
                .name("성춘향")
                .email("spring@example.com")
                .password("Password!123")
                //.role(Role.TEAM_MEMBER)
                .phoneNumber("01011112222")
                .build();

        when(userRepository.existsByEmailOrStudentId(any(), any())).thenReturn(true);
        when(userRepository.existsByStudentId(any())).thenReturn(false); // studentId 중복 아님 -> phone 중복으로 간주

        // when
        CustomException ex = assertThrows(CustomException.class, () -> userService.signup(req));

        // then
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_PHONE);

        verify(userRepository).existsByEmail("spring@example.com");
        verify(userRepository).existsByPhoneNumber("01011112222");
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("회원가입 실패: DB 무결성 제약 위반 시 CustomException(DUPLICATE_USER)")
    void signup_dataIntegrityViolation_throws() {
        // given
        SignupRequest req = SignupRequest.builder()
                .name("임꺽정")
                .email("im@example.com")
                .password("Pw123456!")
                //.role(Role.TEAM_MEMBER)
                .phoneNumber("01077778888")
                .build();

        when(userRepository.existsByEmailOrStudentId(any(), any())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(req.getPhoneNumber())).thenReturn(false);
        when(passwordEncoder.encode(req.getPassword())).thenReturn("ENCODED_PW");

        when(userRepository.save(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("constraint"));

        // when
        CustomException ex = assertThrows(CustomException.class, () -> userService.signup(req));

        // then
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_USER);
    }

//    @Test
//    @DisplayName("OAuth 로그인: 기존 계정이 있으면 해당 User 반환")
//    void findOrCreateUser_existingUser() {
//        // given
//        OauthUserInfo mockInfo = new OauthUserInfo() {
//            @Override public AuthProvider getProvider() { return AuthProvider.GOOGLE; }
//            @Override public String getProviderUid() { return "google-123"; }
//            @Override public String getName() { return "홍길동"; }
//            @Override public String getAccessToken() { return "mock-access-token"; }
//        };
//
//        User existingUser = User.builder()
//                .userId(UUID.randomUUID())
//                .name("홍길동")
//                .role(Role.TEAM_MEMBER)
//                .build();
//
//        UserOauthAccount account = UserOauthAccount.builder()
//                .user(existingUser)
//                .provider(AuthProvider.GOOGLE)
//                .providerUid("google-123")
//                .build();
//
//        when(oauthAccountRepository.findByProviderAndProviderUid(AuthProvider.GOOGLE, "google-123"))
//                .thenReturn(Optional.of(account));
//
//        // when
//        User result = userService.findOrCreateUser(mockInfo);
//
//        // then
//        assertThat(result).isSameAs(existingUser);
//        verify(oauthAccountRepository).findByProviderAndProviderUid(AuthProvider.GOOGLE, "google-123");
//        verifyNoMoreInteractions(userRepository); // 새 저장 안 함
//    }
//
//    @Test
//    @DisplayName("OAuth 로그인: 기존 계정이 없으면 새 User + UserOauthAccount 생성")
//    void findOrCreateUser_newUser() {
//        // given
//        OauthUserInfo mockInfo = new OauthUserInfo() {
//            @Override public AuthProvider getProvider() { return AuthProvider.KAKAO; }
//            @Override public String getProviderUid() { return "kakao-999"; }
//            @Override public String getName() { return "카카오유저"; }
//            @Override public String getAccessToken() { return "mock-access-token"; }
//        };
//
//        when(oauthAccountRepository.findByProviderAndProviderUid(AuthProvider.KAKAO, "kakao-999"))
//                .thenReturn(Optional.empty());
//
//        User newUser = User.builder()
//                .userId(UUID.randomUUID())
//                .name("카카오유저")
//                .role(Role.TEAM_MEMBER)
//                .build();
//
//        when(userRepository.save(any(User.class))).thenReturn(newUser);
//
//        // when
//        User result = userService.findOrCreateUser(mockInfo);
//
//        // then
//        assertThat(result.getName()).isEqualTo("카카오유저");
//        assertThat(result.getRole()).isEqualTo(Role.TEAM_MEMBER);
//
//        verify(userRepository).save(any(User.class));
//        verify(oauthAccountRepository).save(any(UserOauthAccount.class));
//    }

     */



    @Test
    @DisplayName("회원정보 수정 실패: 존재하지 않는 사용자일 경우 예외 발생")
    void updateUser_notFound_throws() {
        // given
        UUID nonExistingId = UUID.randomUUID();
        UserUpdateRequest request = new UserUpdateRequest();

        CustomException exception = assertThrows(CustomException.class,
                () -> userService.updateUser(nonExistingId, request));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("회원정보 수정: 모든 필드가 null이면 기존 정보 그대로 유지")
    void updateUser_allFieldsNull_noChanges() {
        // given
        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .userId(userId)
                .name("원래이름")
                .phoneNumber("010-1111-1111")
                .passwordHash("OLD_HASH")
                .role(Role.TEAM_MEMBER)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        // 모든 필드가 null인 요청 DTO
        var request = new org.sejongisc.backend.user.dto.UserUpdateRequest();

        // when
        userService.updateUser(userId, request);

        // then
        assertAll(
                () -> assertThat(existingUser.getName()).isEqualTo("원래이름"),
                () -> assertThat(existingUser.getPhoneNumber()).isEqualTo("010-1111-1111"),
                () -> assertThat(existingUser.getPasswordHash()).isEqualTo("OLD_HASH")
        );

        verify(userRepository).findById(userId);
        verify(userRepository).save(existingUser);
        verifyNoInteractions(passwordEncoder); // 비밀번호 인코더 안 씀
    }







}
