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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;
    private final UserOauthAccountRepository oauthAccountRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest req) throws OAuth2AuthenticationException {
        // log.info("[CustomOidcUserService] Google OIDC loadUser START");

        OidcUser oidcUser = super.loadUser(req);

        Map<String, Object> original = oidcUser.getAttributes();
       // log.info("OIDC claims: {}", original);

        // SuccessHandler가 필요로 하는 attributes 넣기
        String provider = "google";             // provider
        String providerUid = (String) original.get("sub");  // 구글 고유 ID
        String email = (String) original.get("email");
        String name = (String) original.get("name");

        // 신규 가입 or 기존 유저 조회
        User user = oauthAccountRepository
                .findByProviderAndProviderUid(AuthProvider.GOOGLE, providerUid)
                .map(UserOauthAccount::getUser)
                .orElseGet(() -> {
                    // (1) User 생성
                    User newUser = User.builder()
                            .email(email)
                            .name(name)
                            .role(Role.TEAM_MEMBER)
                            .build();
                    User savedUser = userRepository.save(newUser);

                    // (2) UserOauthAccount 생성
                    UserOauthAccount oauth = UserOauthAccount.builder()
                            .user(savedUser)
                            .provider(AuthProvider.GOOGLE)
                            .providerUid(providerUid)
                            .build();
                    oauthAccountRepository.save(oauth);

                  //  log.info("[CustomOidcUserService] 신규 User 및 UserOauthAccount 생성됨");
                    return savedUser;
                });

        Map<String, Object> attrs = new HashMap<>(original);
        attrs.put("provider", provider);
        attrs.put("providerUid", providerUid);
        attrs.put("email", user.getEmail());
        attrs.put("name", user.getName());
        attrs.put("userId", user.getUserId()); // SuccessHandler가 필요로 함

        // 여기서 attrs를 defaultOidcUser에 직접 넣음
        return new DefaultOidcUser(
                oidcUser.getAuthorities(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "sub"
        ) {
            @Override
            public Map<String, Object> getAttributes() {
                return attrs; // 커스텀 attributes 적용
            }
        };
    }
}
