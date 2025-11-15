package org.sejongisc.backend.common.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.dao.UserOauthAccountRepository;
import org.sejongisc.backend.auth.entity.AuthProvider;
import org.sejongisc.backend.auth.entity.UserOauthAccount;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.auth.service.RefreshTokenService;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
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

        // 6.  HttpOnly 쿠키로 refreshToken 저장
        ResponseCookie accessCookie = ResponseCookie.from("access", accessToken)
                .httpOnly(true)
                .secure(false)   // 로컬 개발
                .sameSite("Lax")   // 로컬에서는 None 비추천
                .path("/")
                .maxAge(60L * 60)  // 1 hour
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh", refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(60L * 60 * 24 * 14) // 2 weeks
                .build();


        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());


        // 7. 프론트로 redirect
        String redirectUrl = "http://localhost:5173/oauth/success";
//                + "?accessToken=" + accessToken
//                + "&name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
//                + "&userId=" + userId;

     //    log.info("[OAuth2 Redirect] {}", redirectUrl);

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

}
