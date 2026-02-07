//package org.sejongisc.backend.common.auth.service.oauth2;
//
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import jakarta.transaction.Transactional;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.sejongisc.backend.common.auth.dto.oauth.GithubTokenResponse;
//import org.sejongisc.backend.common.auth.dto.oauth.GithubUserInfoResponse;
//import org.sejongisc.backend.common.auth.entity.AuthProvider;
//import org.sejongisc.backend.common.auth.entity.UserOauthAccount;
//import org.sejongisc.backend.common.auth.jwt.JwtProvider;
//import org.sejongisc.backend.common.auth.repository.UserOauthAccountRepository;
//import org.sejongisc.backend.common.auth.service.RefreshTokenService;
//import org.sejongisc.backend.user.entity.Role;
//import org.sejongisc.backend.user.entity.User;
//import org.sejongisc.backend.user.repository.UserRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.env.Environment;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatusCode;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseCookie;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
//import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
//import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
//import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
//import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
//import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
//import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
//import org.springframework.security.oauth2.core.oidc.user.OidcUser;
//import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
//import org.springframework.security.oauth2.core.user.OAuth2User;
//import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
//import org.springframework.stereotype.Component;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.BodyInserters;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.publisher.Mono;
//
//import java.io.IOException;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.function.Function;
//
//@Slf4j
//@Service("GITHUB")
//public class GithubServiceImpl implements Oauth2Service<GithubTokenResponse, GithubUserInfoResponse> {
//
//    private final String clientId;
//    private final String clientSecret;
//
//    private final String TOKEN_URL;
//    private final String USERINFO_URL;
//
//    @Autowired
//    public GithubServiceImpl(
//            @Value("${github.client.id}") String clientId,
//            @Value("${github.client.secret}") String clientSecret) {
//        this.clientId = clientId;
//        this.clientSecret = clientSecret;
//        this.TOKEN_URL = "https://github.com/login/oauth/access_token";
//        this.USERINFO_URL = "https://api.github.com/user";
//    }
//
//    // ✅ 테스트용 생성자
//    public GithubServiceImpl(String clientId, String clientSecret,
//                                String tokenUrl, String userInfoUrl) {
//        this.clientId = clientId;
//        this.clientSecret = clientSecret;
//        this.TOKEN_URL = tokenUrl;
//        this.USERINFO_URL = userInfoUrl;
//    }
//
//    @Override
//    public GithubTokenResponse getAccessToken(String code) {
//        GithubTokenResponse tokenResponse = WebClient.create(TOKEN_URL).post()
//                .uri(uriBuilder -> uriBuilder.build(true))
//                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
//                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
//                .body(BodyInserters.fromFormData("client_id", clientId)
//                        .with("client_secret", clientSecret)
//                        .with("code", code))
//                .retrieve()
//                .onStatus(HttpStatusCode::is4xxClientError,
//                        clientResponse -> Mono.error(new RuntimeException("Invalid Parameter")))
//                .onStatus(HttpStatusCode::is5xxServerError,
//                        clientResponse -> Mono.error(new RuntimeException("Internal Server Error")))
//                .bodyToMono(GithubTokenResponse.class)
//                .block();
//
//        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
//            throw new RuntimeException("Token response is empty");
//        }
//
//        Function<String, String> mask = token -> {
//            if(token == null || token.length() < 8) return "****";
//            return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
//        };
//
//        log.debug(" [Github Service] Access Token ------> {}", mask.apply(tokenResponse.getAccessToken()));
//        log.debug(" [Github Service] Scope        ------> {}", mask.apply(tokenResponse.getScope()));
//
//        return tokenResponse;
//    }
//
//    @Override
//    public GithubUserInfoResponse getUserInfo(String accessToken) {
//        GithubUserInfoResponse userInfo = WebClient.create(USERINFO_URL).get()
//                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
//                .retrieve()
//                .onStatus(HttpStatusCode::is4xxClientError,
//                        clientResponse -> Mono.error(new RuntimeException("Invalid Parameter")))
//                .onStatus(HttpStatusCode::is5xxServerError,
//                        clientResponse -> Mono.error(new RuntimeException("Internal Server Error")))
//                .bodyToMono(GithubUserInfoResponse.class)
//                .block();
//
//        if (userInfo == null) {
//            throw new RuntimeException("UserInfo response is empty");
//        }
//
//        if (log.isDebugEnabled()) {
//            log.debug(" [Github Service] ID    ------> {}", userInfo.getId());
//            log.debug(" [Github Service] Login ------> {}", userInfo.getLogin());
//            log.debug(" [Github Service] Name  ------> {}", userInfo.getName());
//        }
//
//        return userInfo;
//    }
//
//    @Slf4j
//    @Service
//    @RequiredArgsConstructor
//    @Transactional
//    public static class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
//        private final UserRepository userRepository;
//        private final UserOauthAccountRepository oauthAccountRepository;
//
//        @Override
//        public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {
//            // log.info("[CustomOAuth2UserService] loadUser START");
//
//            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate =
//                    new DefaultOAuth2UserService();
//            OAuth2User oAuth2User = delegate.loadUser(req);
//
//            String provider = req.getClientRegistration().getRegistrationId(); // google, kakao, github
//            Map<String, Object> attrs = oAuth2User.getAttributes();
//
//            String providerUid;
//            String email;
//            String name;
//
//            // log.info("[OAuth2] Provider = {}", provider);
//            if (log.isDebugEnabled()) {
//                log.debug("[OAuth2] Attributes = {}", attrs);
//            }
//
//            switch (provider) {
//                case "google" -> {
//                    providerUid = (String) attrs.get("sub");
//                    email = (String) attrs.get("email");
//                    name = (String) attrs.get("name");
//                }
//                case "kakao" -> {
//                    providerUid = attrs.get("id").toString();
//                    Map<String, Object> kakaoAccount = (Map<String, Object>) attrs.get("kakao_account");
//                    email = (String) kakaoAccount.get("email");  // null 가능
//                    Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
//                    name = (String) profile.get("nickname");
//                }
//                case "github" -> {
//                    providerUid = attrs.get("id").toString();
//                    email = (String) attrs.get("email");
//                    name = (String) attrs.get("login"); // GitHub은 login이 닉네임
//                }
//                default -> throw new RuntimeException("지원하지 않는 provider: " + provider);
//            }
//
//            // log.info("provider={}, providerUid={}, email={}, name={}", provider, providerUid, email, name);
//
//            final String fProviderUid = providerUid;
//            final String fEmail = email;
//            final String fName = name;
//            final AuthProvider fAuthProvider = AuthProvider.valueOf(provider.toUpperCase());
//
//            User user = oauthAccountRepository
//                    .findByProviderAndProviderUid(AuthProvider.from(provider), providerUid)
//                    .map(UserOauthAccount::getUser)
//                    .orElseGet(() -> {
//                        User newUser = User.builder()
//                                .email(email)
//                                .name(name)
//                                .role(Role.TEAM_MEMBER)
//                                .build();
//                        User saved = userRepository.save(newUser);
//
//                        UserOauthAccount oauth = UserOauthAccount.builder()
//                                .user(saved)
//                                .provider(AuthProvider.from(provider))
//                                .providerUid(providerUid)
//                                .build();
//                        oauthAccountRepository.save(oauth);
//
//                        return saved;
//                    });
//
//            // log.info("[CustomOAuth2UserService] User resolved → returning OAuth2User");
//
//            Map<String, Object> attributes = new java.util.HashMap<>();
//            attributes.put("provider", provider);           // google / kakao / github
//            attributes.put("providerUid", providerUid);     // 소셜 계정 UID
//            attributes.put("email", user.getEmail());       // DB email
//            attributes.put("name", user.getName());
//            attributes.put("userId", user.getUserId());     // DB user uuid
//
//            return new DefaultOAuth2User(
//                    List.of(new SimpleGrantedAuthority("ROLE_TEAM_MEMBER")),
//                    attributes,
//                    "userId" // 또는 "email" -> email null 이면 id가 더 안전
//            );
//
//        }
//    }
//
//    @Slf4j
//    @Service
//    @RequiredArgsConstructor
//    @Transactional
//    public static class CustomOidcUserService extends OidcUserService {
//
//        private final UserRepository userRepository;
//        private final UserOauthAccountRepository oauthAccountRepository;
//
//        @Override
//        public OidcUser loadUser(OidcUserRequest req) throws OAuth2AuthenticationException {
//            // log.info("[CustomOidcUserService] Google OIDC loadUser START");
//
//            OidcUser oidcUser = super.loadUser(req);
//
//            Map<String, Object> original = oidcUser.getAttributes();
//           // log.info("OIDC claims: {}", original);
//
//            // SuccessHandler가 필요로 하는 attributes 넣기
//            String provider = "google";             // provider
//            String providerUid = (String) original.get("sub");  // 구글 고유 ID
//            String email = (String) original.get("email");
//            String name = (String) original.get("name");
//
//            // 신규 가입 or 기존 유저 조회
//            User user = oauthAccountRepository
//                    .findByProviderAndProviderUid(AuthProvider.GOOGLE, providerUid)
//                    .map(UserOauthAccount::getUser)
//                    .orElseGet(() -> {
//                        // (1) User 생성
//                        User newUser = User.builder()
//                                .email(email)
//                                .name(name)
//                                .role(Role.TEAM_MEMBER)
//                                .build();
//                        User savedUser = userRepository.save(newUser);
//
//                        // (2) UserOauthAccount 생성
//                        UserOauthAccount oauth = UserOauthAccount.builder()
//                                .user(savedUser)
//                                .provider(AuthProvider.GOOGLE)
//                                .providerUid(providerUid)
//                                .build();
//                        oauthAccountRepository.save(oauth);
//
//                      // log.info("[CustomOidcUserService] 신규 User 및 UserOauthAccount 생성됨");
//                        return savedUser;
//                    });
//
//            Map<String, Object> attrs = new HashMap<>(original);
//            attrs.put("provider", provider);
//            attrs.put("providerUid", providerUid);
//            attrs.put("email", user.getEmail());
//            attrs.put("name", user.getName());
//            attrs.put("userId", user.getUserId()); // SuccessHandler가 필요로 함
//
//            // 여기서 attrs를 defaultOidcUser에 직접 넣음
//            return new DefaultOidcUser(
//                    oidcUser.getAuthorities(),
//                    oidcUser.getIdToken(),
//                    oidcUser.getUserInfo(),
//                    "sub"
//            ) {
//                @Override
//                public Map<String, Object> getAttributes() {
//                    return attrs; // 커스텀 attributes 적용
//                }
//            };
//        }
//    }
//
//    @Slf4j
//    @Component
//    @RequiredArgsConstructor
//    public static class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
//
//        private final JwtProvider jwtProvider;
//        private final RefreshTokenService refreshTokenService;
//        private final UserRepository userRepository;
//        private final UserOauthAccountRepository userOauthAccountRepository;
//        private final Environment env;
//
//        @Value("${app.oauth2.redirect-success}")
//        private String redirectSuccessBase;
//
//        @Override
//        public void onAuthenticationSuccess(
//            HttpServletRequest request,
//            HttpServletResponse response,
//            Authentication authentication) throws IOException {
//
//            // log.info("[OAuth2SuccessHandler] SUCCESS HANDLER CALLED!");
//
//            if (!(authentication.getPrincipal() instanceof DefaultOAuth2User oauthUser)) {
//                throw new IllegalStateException("Unknown principal type: " + authentication.getPrincipal().getClass());
//            }
//
//            // 1. CustomOAuth2UserService에서 넣어준 attributes 가져오기
//            Map<String, Object> attrs = oauthUser.getAttributes();
//
//            String providerStr = (String) attrs.get("provider");
//            String providerUid = (String) attrs.get("providerUid");
//            if (providerStr == null) {
//                throw new IllegalStateException("OAuth provider attribute missing from attributes");
//            }
//
//            AuthProvider provider =
//                    switch (providerStr) {
//                        case "kakao" -> AuthProvider.KAKAO;
//                        case "github" -> AuthProvider.GITHUB;
//                        case "google" -> AuthProvider.GOOGLE;
//                        default -> throw new IllegalStateException("Unknown OAuth provider: " + providerStr);
//                    };
//
//
//            // log.info("[OAuth2SuccessHandler] provider={}, providerUid={}", provider, providerUid);
//
//            // DB 조회
//            UserOauthAccount account = userOauthAccountRepository
//                    .findByProviderAndProviderUid(provider, providerUid)
//                    .orElseThrow(() -> new RuntimeException("소셜 계정이 DB에 없습니다. (회원가입 필요)"));
//
//            User user = userRepository.findById(account.getUser().getUserId())
//                    .orElseThrow(() -> new RuntimeException("User not found"));
//
//            // JWT 생성
//            String accessToken = jwtProvider.createToken(
//                    user.getUserId(),
//                    user.getRole(),
//                    user.getEmail()
//            );
//
//
//            // 4. RefreshToken 생성
//            String refreshToken = jwtProvider.createRefreshToken(user.getUserId());
//            // 5. RefreshToken 저장(DB or Redis)
//            refreshTokenService.saveOrUpdateToken(user.getUserId(), refreshToken);
//
//            String[] activeProfiles = env.getActiveProfiles();
//            List<String> profiles = Arrays.asList(activeProfiles);
//
//            boolean isProd = profiles.contains("prod");
//            boolean isDev  = profiles.contains("dev");
//
//    // SameSite, Secure 설정 (dev도 prod와 동일하게)
//            String sameSite = (isProd || isDev) ? "None" : "Lax";
//            boolean secure  = (isProd || isDev);
//
//    // 도메인 설정
//            String domain;
//            if (isProd) {
//                domain = "sjusisc.com"; // 운영 도메인
//            } else if (isDev) {
//                domain = "sisc-web.duckdns.org"; // 개발 도메인
//            } else {
//                domain = "localhost"; // 기본값
//            }
//
//
//
//
//            // 6.  HttpOnly 쿠키로 refreshToken 저장
//            ResponseCookie.ResponseCookieBuilder accessCookieBuilder = ResponseCookie.from("access", accessToken)
//                    .httpOnly(true)
//                    .secure(secure)    // 로컬=false, 배포=true
//                    .sameSite(sameSite)  // 로컬= "Lax", 배포="None"
//                    .path("/")
//                    .maxAge(60L * 60);  // 1 hour
//
//            // 로컬 환경에서는 domain 설정하지 않음
//            if (isProd || isDev) {
//                accessCookieBuilder.domain(domain);
//            }
//
//            ResponseCookie.ResponseCookieBuilder refreshCookieBuilder = ResponseCookie.from("refresh", refreshToken)
//                    .httpOnly(true)
//                    .secure(secure)
//                    .sameSite(sameSite)
//                    .path("/")
//                    .maxAge(60L * 60 * 24 * 14); // 2 weeks
//
//            // 로컬 환경에서는 domain 설정하지 않음
//            if (isProd || isDev) {
//                refreshCookieBuilder.domain(domain);
//            }
//
//            ResponseCookie accessCookie = accessCookieBuilder.build();
//            ResponseCookie refreshCookie = refreshCookieBuilder.build();
//
//
//            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
//            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
//
//
//            // 7. 프론트로 redirect
//            // application-local.yml → http://localhost:5173/oauth/success
//            // application-prod.yml → https://sisc-web.duckdns.org/oauth/success
//            //String redirectUrl = redirectSuccessBase;
//    //                + "?accessToken=" + accessToken
//    //                + "&name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
//    //                + "&userId=" + userId;
//
//           // log.info("[OAuth2 Redirect] {}", redirectUrl);
//
//            getRedirectStrategy().sendRedirect(request, response, redirectSuccessBase);
//        }
//
//    }
//}
