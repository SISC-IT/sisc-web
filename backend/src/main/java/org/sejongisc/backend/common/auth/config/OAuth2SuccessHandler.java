package org.sejongisc.backend.common.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.sejongisc.backend.auth.dao.UserOauthAccountRepository;
import org.sejongisc.backend.auth.entity.AuthProvider;
import org.sejongisc.backend.auth.entity.UserOauthAccount;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.auth.service.RefreshTokenService;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final UserOauthAccountRepository userOauthAccountRepository;
    private final Environment env;

    @Value("${app.oauth2.redirect-success}")
    private String redirectSuccessBase;

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication) throws IOException{

        // log.info("[OAuth2SuccessHandler] SUCCESS HANDLER CALLED!");

        if (!(authentication.getPrincipal() instanceof DefaultOAuth2User oauthUser)) {
            throw new IllegalStateException("Unknown principal type: " + authentication.getPrincipal().getClass());
        }

        // 1. CustomOAuth2UserService에서 넣어준 attributes 가져오기
        Map<String, Object> attrs = oauthUser.getAttributes();

        String providerStr = (String) attrs.get("provider");
        String providerUid = (String) attrs.get("providerUid");

        if (providerStr == null) {
            throw new IllegalStateException("OAuth provider attribute missing from attributes");
        }

        AuthProvider provider =
                switch (providerStr) {
                    case "kakao" -> AuthProvider.KAKAO;
                    case "github" -> AuthProvider.GITHUB;
                    case "google" -> AuthProvider.GOOGLE;
                    default -> throw new IllegalStateException("Unknown OAuth provider: " + providerStr);
                };


        // log.info("[OAuth2SuccessHandler] provider={}, providerUid={}", provider, providerUid);

        // DB 조회
        UserOauthAccount account = userOauthAccountRepository
                .findByProviderAndProviderUid(provider, providerUid)
                .orElseThrow(() -> new RuntimeException("소셜 계정이 DB에 없습니다. (회원가입 필요)"));

        User user = userRepository.findById(account.getUser().getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // JWT 생성
        String accessToken = jwtProvider.createToken(
                user.getUserId(),
                user.getRole(),
                user.getEmail()
        );


        // 4. RefreshToken 생성
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId());

        // 5. RefreshToken 저장(DB or Redis)
        refreshTokenService.saveOrUpdateToken(user.getUserId(), refreshToken);

        String[] activeProfiles = env.getActiveProfiles();

        boolean isProd = Arrays.asList(activeProfiles).contains("prod");
        boolean isDev  = Arrays.asList(activeProfiles).contains("dev");

        // SameSite, Secure 설정
        String sameSite = isProd ? "None" : "Lax";
        boolean secure  = isProd;

        // 도메인 설정
        String domain;
        if (isProd) {
            domain = "sjusisc";  // 🔥 운영 도메인
        } else if (isDev) {
            domain = "sisc-web.duckdns.org";  // 🔥 개발 도메인
        } else {
            domain = "localhost";  // 🔥 기본값
        }




        // 6.  HttpOnly 쿠키로 refreshToken 저장
        ResponseCookie accessCookie = ResponseCookie.from("access", accessToken)
                .httpOnly(true)
                .secure(secure)    // 로컬=false, 배포=true
                .sameSite(sameSite)  // 로컬= "Lax", 배포="None"
                .domain(domain)
                .path("/")
                .maxAge(60L * 60)  // 1 hour
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh", refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .domain(domain)
                .path("/")
                .maxAge(60L * 60 * 24 * 14) // 2 weeks
                .build();

      
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());


        // 7. 프론트로 redirect
        // application-local.yml → http://localhost:5173/oauth/success
        // application-prod.yml → https://sisc-web.duckdns.org/oauth/success
        //String redirectUrl = redirectSuccessBase;
//                + "?accessToken=" + accessToken
//                + "&name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
//                + "&userId=" + userId;

       // log.info("[OAuth2 Redirect] {}", redirectUrl);

        getRedirectStrategy().sendRedirect(request, response, redirectSuccessBase);
    }

}
