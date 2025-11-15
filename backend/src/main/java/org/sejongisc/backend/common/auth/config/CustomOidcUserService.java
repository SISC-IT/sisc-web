package org.sejongisc.backend.common.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.dao.UserOauthAccountRepository;
import org.sejongisc.backend.user.dao.UserRepository;
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
        Map<String, Object> attrs = new HashMap<>(original);
        attrs.put("provider", "google");                // provider
        attrs.put("providerUid", original.get("sub"));  // 구글 고유 ID
        attrs.put("email", original.get("email"));
        attrs.put("name", original.get("name"));

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
