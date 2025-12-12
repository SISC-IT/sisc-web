package org.sejongisc.backend.common.auth.config;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.dao.UserOauthAccountRepository;
import org.sejongisc.backend.auth.entity.AuthProvider;
import org.sejongisc.backend.auth.entity.UserOauthAccount;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.service.UserServiceImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomOAuth2UserService implements OAuth2UserService <OAuth2UserRequest, OAuth2User> {
    private final UserRepository userRepository;
    private final UserOauthAccountRepository oauthAccountRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {

        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(req);

        String provider = req.getClientRegistration().getRegistrationId(); // google, kakao, github
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String providerUid;
        String email;
        String name;

        if (log.isDebugEnabled()) {
            log.debug("[OAuth2] Attributes = {}", attrs);
        }

        switch (provider) {
            case "google" -> {
                providerUid = (String) attrs.get("sub");
                email = (String) attrs.get("email");
                name = (String) attrs.get("name");
            }
            case "kakao" -> {
                providerUid = attrs.get("id").toString();
                Map<String, Object> kakaoAccount = (Map<String, Object>) attrs.get("kakao_account");
                email = kakaoAccount == null ? null : (String) kakaoAccount.get("email");  // null 가능
                Map<String, Object> profile = kakaoAccount == null ? null : (Map<String, Object>) kakaoAccount.get("profile");
                name = profile == null ? null : (String) profile.get("nickname");
            }
            case "github" -> {
                providerUid = attrs.get("id").toString();
                email = (String) attrs.get("email");      // GitHub은 null 자주 나옴(권한/공개설정)
                name = (String) attrs.get("login");
            }
            default -> throw new RuntimeException("지원하지 않는 provider: " + provider);
        }

        AuthProvider authProvider = AuthProvider.from(provider);

        // 1) (provider, providerUid)로 먼저 찾는다 (기존 OAuth 로그인 사용자)
        Optional<UserOauthAccount> oauthLinkOpt =
                oauthAccountRepository.findByProviderAndProviderUid(authProvider, providerUid);

        User user;

        if (oauthLinkOpt.isPresent()) {
            user = oauthLinkOpt.get().getUser();
        } else {
            // 2) 링크가 없으면 email로 기존 로컬/회원가입 유저를 찾는다 (핵심!)
            Optional<User> userByEmailOpt = Optional.empty();
            if (email != null && !email.isBlank()) {
                userByEmailOpt = userRepository.findUserByEmail(email);
            }

            if (userByEmailOpt.isPresent()) {
                // 3) 기존 유저가 있으면: 새 유저 만들지 말고 OAuth 링크만 추가
                user = userByEmailOpt.get();

                // (선택) 이미 같은 provider로 링크가 있으면 중복 저장 방지
                // repository에 메서드 없으면 try/catch로 unique constraint에 맡겨도 됨
                boolean alreadyLinked = oauthAccountRepository
                        .existsByProviderAndUser(authProvider, user);

                if (!alreadyLinked) {
                    UserOauthAccount oauth = UserOauthAccount.builder()
                            .user(user)
                            .provider(authProvider)
                            .providerUid(providerUid)
                            .build();
                    oauthAccountRepository.save(oauth);
                }

            } else {
                // 4) email로도 못 찾으면 신규 생성 + 링크
                User newUser = User.builder()
                        .email(email) // null 가능성: DB 제약(NOT NULL/UNIQUE) 있으면 정책 필요
                        .name(name)
                        .role(Role.TEAM_MEMBER)
                        .build();

                User saved = userRepository.save(newUser);

                UserOauthAccount oauth = UserOauthAccount.builder()
                        .user(saved)
                        .provider(authProvider)
                        .providerUid(providerUid)
                        .build();
                oauthAccountRepository.save(oauth);

                user = saved;
            }
        }

        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("provider", provider);
        attributes.put("providerUid", providerUid);
        attributes.put("email", user.getEmail());
        attributes.put("name", user.getName());
        attributes.put("userId", user.getUserId());

        return new DefaultOAuth2User(
                // 기존처럼 고정 ROLE 주고 싶으면 그대로 두고,
                // 권한을 user role 기반으로 주고 싶으면 아래 주석 라인으로 교체해도 됨
                List.of(new SimpleGrantedAuthority("ROLE_TEAM_MEMBER")),
                // List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                attributes,
                "userId"
        );
    }

}
