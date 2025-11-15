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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomOAuth2UserService implements OAuth2UserService <OAuth2UserRequest, OAuth2User> {
    private final UserRepository userRepository;
    private final UserOauthAccountRepository oauthAccountRepository;



    @Override
    public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {

        log.info("[CustomOAuth2UserService loadUser start]");
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        OAuth2User oauthUser = delegate.loadUser(req);

        String provider = req.getClientRegistration().getRegistrationId();
        Map<String, Object> attrs = oauthUser.getAttributes();

        log.info("[OAuth2] Provider = {}", provider);
        if (log.isDebugEnabled()) {
            log.debug("[OAuth2] Attributes = {}", attrs);
        }

        String providerUid;
        String email = null;
        String name = null;

        if (provider.equals("google")) {
            providerUid = (String) attrs.get("sub");
            email = (String) attrs.get("email");
            name  = (String) attrs.get("name");

        } else if (provider.equals("kakao")) {
            providerUid = String.valueOf(attrs.get("id"));
            Map<String, Object> account = (Map<String, Object>) attrs.get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) account.get("profile");

            email = (String) account.get("email");
            if (email == null) email = "no-email@kakao.com";

            name = (String) profile.get("nickname");

        } else if (provider.equals("github")) {
            providerUid = String.valueOf(attrs.get("id"));
            email = (String) attrs.get("email");
            if (email == null) email = "no-email@github.com";

            name = (String) attrs.get("name");
            if (name == null) name = email;

        } else {
            throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        }

        // 순환참조 해결: 여기서 userService 호출하지 않음

        final String fProviderUid = providerUid;
        final String fEmail = email;
        final String fName = name;
        final AuthProvider fAuthProvider = AuthProvider.valueOf(provider.toUpperCase());

        User user = oauthAccountRepository
                .findByProviderAndProviderUid(fAuthProvider, providerUid)
                .map(UserOauthAccount::getUser)
                .orElseGet(() -> {

                    User newUser = User.builder()
                            .email(fEmail)
                            .name(fName)
                            .role(Role.TEAM_MEMBER)
                            .build();

                    User saved = userRepository.save(newUser);

                    UserOauthAccount oauth = UserOauthAccount.builder()
                            .user(saved)
                            .provider(fAuthProvider)
                            .providerUid(fProviderUid)
                            .build();

                    oauthAccountRepository.save(oauth);

                    return saved;
                });
        log.info("[CustomOAuth2UserService loadUser end]");
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_TEAM_MEMBER")),
                Map.of(
                        "id", user.getUserId(),
                        "email", user.getEmail(),
                        "name", user.getName()
                ),
                "email"
        );
    }
}
