package org.sejongisc.backend.user.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.jwt.JwtProvider;
import org.sejongisc.backend.user.dto.KakaoIdStatusDto;
import org.sejongisc.backend.user.dto.KakaoUserInfoResponse;
import org.sejongisc.backend.user.dto.LoginResponse;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.service.KakaoService;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class KakaoAuthLoginController {

    private final KakaoService kakaoService;
    private final UserService userService;
    private final JwtProvider jwtProvider;

//    private static final String FRONT_BASEURL = "http://localhost:5173";

    @GetMapping("/auth/login/kakao")
    public ResponseEntity<LoginResponse> KakaoLogin(@RequestParam("code") String code) {
        String accessToken = kakaoService.getAccessTokenFromKakao(code);

        KakaoUserInfoResponse userInfo = kakaoService.getUserInfo(accessToken);

        // DB 조회 or 신규 가입
        User user = userService.findOrCreateUser(userInfo);

        String jwt = jwtProvider.createToken(user.getUserId(), user.getRole());

        // HttpOnly 쿠키에 담기
        ResponseCookie cookie = ResponseCookie.from("access", jwt)
                .httpOnly(true)
                .secure(true)   // 배포 환경에서는 true
                .sameSite("None")   // CORS 대응
                .path("/")
                .maxAge(60 * 60)
                .build();

        // LoginResponse 생성
        LoginResponse response = LoginResponse.builder()
                .accessToken(jwt)
                .userId(user.getUserId())
                // .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .phoneNumber(user.getPhoneNumber())
                .point(user.getPoint())
                .build();


        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

}