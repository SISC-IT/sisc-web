//package org.sejongisc.backend.user.controller;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.sejongisc.backend.common.auth.jwt.JwtProvider;
//import org.sejongisc.backend.user.dto.GoogleTokenResponse;
//import org.sejongisc.backend.user.dto.GoogleUserInfoResponse;
//import org.sejongisc.backend.user.dto.LoginResponse;
//import org.sejongisc.backend.user.entity.User;
//import org.sejongisc.backend.user.service.GoogleService;
//import org.sejongisc.backend.user.service.UserService;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//@Slf4j
//@RestController
//@RequiredArgsConstructor
//public class GoogleAuthLoginController {
//
//    private final GoogleService googleService;
//    private final UserService userService;
//    private final JwtProvider jwtProvider;
//
//    @GetMapping("/auth/login/google")
//    public ResponseEntity<LoginResponse> googleLogin(@RequestParam("code") String code) {
//        // 인가코드(code)로 구글 토큰 발급
//        GoogleTokenResponse tokenResponse = googleService.getAccessTokenFromGoogle(code);
//        String accessToken = tokenResponse.getAccessToken();
//
//        // 구글 UserInfo API로 사용자 정보 가져오기
//        GoogleUserInfoResponse userInfo = googleService.getUserInfo(accessToken);
//
//        // DB 조회 or 신규 가입
//        User user = userService.findOrCreateGoogleUser(userInfo);
//
//        // JWT 발급
//        String jwt = jwtProvider.createToken(user.getUserId(), user.getRole());
//
//        // HttpOnly 쿠키 저장
//        ResponseCookie cookie = ResponseCookie.from("access", jwt)
//                .httpOnly(true)
//                .secure(true)           // 운영 환경 true
//                .sameSite("None")       // CORS 대응
//                .path("/")
//                .maxAge(60 * 60)
//                .build();
//
//        // LoginResponse 생성
//        LoginResponse response = LoginResponse.builder()
//                .accessToken(jwt)
//                .userId(user.getUserId())
//                .name(user.getName())
//                .role(user.getRole())
//                .phoneNumber(user.getPhoneNumber())
//                .point(user.getPoint())
//                .build();
//
//        log.info(" [Google Login] userId={} email={}", user.getUserId(), user.getEmail());
//
//        return ResponseEntity.ok()
//                .header(HttpHeaders.SET_COOKIE, cookie.toString())
//                .body(response);
//    }
//}
