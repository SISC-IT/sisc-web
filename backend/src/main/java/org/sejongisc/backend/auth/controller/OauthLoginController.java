package org.sejongisc.backend.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.auth.dto.GithubUserInfoResponse;
import org.sejongisc.backend.auth.dto.GoogleUserInfoResponse;
import org.sejongisc.backend.auth.dto.KakaoUserInfoResponse;
import org.sejongisc.backend.auth.dto.LoginResponse;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.auth.oauth.GithubUserInfoAdapter;
import org.sejongisc.backend.auth.oauth.GoogleUserInfoAdapter;
import org.sejongisc.backend.auth.oauth.KakaoUserInfoAdapter;
import org.sejongisc.backend.auth.service.GithubService;
import org.sejongisc.backend.auth.service.GoogleService;
import org.sejongisc.backend.auth.service.KakaoService;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth/login")
@RequiredArgsConstructor
public class OauthLoginController {

    private final GoogleService googleService;
    private final KakaoService kakaoService;
    private final GithubService githubService;
    private final UserService userService;
    private final JwtProvider jwtProvider;

    @GetMapping("/{provider}")
    public ResponseEntity<LoginResponse> OauthLogin(@PathVariable("provider") String provider, @RequestParam("code") String code) {
        User user;

        switch (provider.toUpperCase()) {
            case "GOOGLE"->{
                String accessToken = googleService.getAccessTokenFromGoogle(code).getAccessToken();
                GoogleUserInfoResponse googleInfo = googleService.getUserInfo(accessToken);

                user = userService.findOrCreateUser(new GoogleUserInfoAdapter(googleInfo));
            }
            case "KAKAO" ->{
                String accessToken = kakaoService.getAccessTokenFromKakao(code).getAccessToken();
                KakaoUserInfoResponse kakaoInfo = kakaoService.getUserInfo(accessToken);

                user = userService.findOrCreateUser(new KakaoUserInfoAdapter(kakaoInfo));
            }
            case "GITHUB" ->{
                String accesToken = githubService.getAccessTokenFromGithub(code).getAccessToken();
                GithubUserInfoResponse githubInfo = githubService.getUserInfo(accesToken);

                user = userService.findOrCreateUser(new GithubUserInfoAdapter(githubInfo));
            }
            default -> throw new IllegalArgumentException("Unknown provider " + provider);
        }

        // JWT 발급
        String jwt = jwtProvider.createToken(user.getUserId(), user.getRole());

        // HttpOnly 쿠키에 담기
        ResponseCookie cookie = ResponseCookie.from("access", jwt)
                .httpOnly(true)
                .secure(true)
                .sameSite("Node")
                .path("/")
                .maxAge(60 * 60)
                .build();

        // LoginResponse 생성
        LoginResponse response = LoginResponse.builder()
                .accessToken(jwt)
                .userId(user.getUserId())
                .name(user.getName())
                .role(user.getRole())
                .phoneNumber(user.getPhoneNumber())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }
}
