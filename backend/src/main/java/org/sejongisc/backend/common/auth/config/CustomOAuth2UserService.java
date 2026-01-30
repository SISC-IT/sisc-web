package org.sejongisc.backend.common.auth.config;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.repository.UserOauthAccountRepository;
import org.sejongisc.backend.auth.entity.AuthProvider;
import org.sejongisc.backend.auth.entity.UserOauthAccount;
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomOAuth2UserService implements OAuth2UserService <OAuth2UserRequest, OAuth2User> {
    private final UserRepository userRepository;
    private final UserOauthAccountRepository oauthAccountRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {
        // log.info("[CustomOAuth2UserService] loadUser START");
        
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate =
                new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(req);

        String provider = req.getClientRegistration().getRegistrationId(); // google, kakao, github
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String providerUid;
        String email;
        String name;

        // log.info("[OAuth2] Provider = {}", provider);
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
                email = (String) kakaoAccount.get("email");  // null 가능
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                name = (String) profile.get("nickname");
            }
            case "github" -> {
                providerUid = attrs.get("id").toString();
                email = (String) attrs.get("email");
                name = (String) attrs.get("login"); // GitHub은 login이 닉네임
            }
            default -> throw new RuntimeException("지원하지 않는 provider: " + provider);
        }

        // log.info("provider={}, providerUid={}, email={}, name={}", provider, providerUid, email, name);

        final String fProviderUid = providerUid;
        final String fEmail = email;
        final String fName = name;
        final AuthProvider fAuthProvider = AuthProvider.valueOf(provider.toUpperCase());

        User user = oauthAccountRepository
                .findByProviderAndProviderUid(AuthProvider.from(provider), providerUid)
                .map(UserOauthAccount::getUser)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .name(name)
                            .role(Role.TEAM_MEMBER)
                            .build();
                    User saved = userRepository.save(newUser);

                    UserOauthAccount oauth = UserOauthAccount.builder()
                            .user(saved)
                            .provider(AuthProvider.from(provider))
                            .providerUid(providerUid)
                            .build();
                    oauthAccountRepository.save(oauth);

                    return saved;
                });

        // log.info("[CustomOAuth2UserService] User resolved → returning OAuth2User");

        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("provider", provider);           // google / kakao / github
        attributes.put("providerUid", providerUid);     // 소셜 계정 UID
        attributes.put("email", user.getEmail());       // DB email
        attributes.put("name", user.getName());
        attributes.put("userId", user.getUserId());     // DB user uuid

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_TEAM_MEMBER")),
                attributes,
                "userId" // 또는 "email" -> email null 이면 id가 더 안전
        );

    }
}
