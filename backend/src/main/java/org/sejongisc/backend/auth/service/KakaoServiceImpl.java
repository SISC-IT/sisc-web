package org.sejongisc.backend.auth.service;

import io.netty.handler.codec.http.HttpHeaderValues;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.dto.KakaoTokenResponse;
import org.sejongisc.backend.auth.dto.KakaoUserInfoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@Slf4j
@Service("KAKAO")
public class KakaoServiceImpl implements Oauth2Service<KakaoTokenResponse, KakaoUserInfoResponse> {

    private final String clientId;
    private final String redirectUri;
    private final String KAUTH_TOKEN_URL_HOST;
    private final String KAUTH_USER_URL_HOST;

    @Autowired
    public KakaoServiceImpl(@Value("${kakao.client.id}") String clientId,
                            @Value("${kakao.redirect.uri}") String redirectUri) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        KAUTH_TOKEN_URL_HOST = "https://kauth.kakao.com";
        KAUTH_USER_URL_HOST = "https://kapi.kakao.com";
    }

    // 테스트에서 MockwebServer 주소 주입할 수 있는 생성자
    public KakaoServiceImpl(String clientId, String redirectUri, String KAUTH_TOKEN_URL_HOST, String KAUTH_USER_URL_HOST) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.KAUTH_TOKEN_URL_HOST = KAUTH_TOKEN_URL_HOST.replace("https://", "http://");
        this.KAUTH_USER_URL_HOST = KAUTH_USER_URL_HOST.replace("https://", "http://");
    }

    @Override
    public KakaoTokenResponse getAccessToken(String code) {

        KakaoTokenResponse kakaoTokenResponse = WebClient.create(KAUTH_TOKEN_URL_HOST).post()
                .uri(uriBuilder -> uriBuilder.path("/oauth/token").build(true))
                .header(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString())
                .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                        .with("client_id", clientId)
                        .with("redirect_uri", redirectUri)
                        .with("code", code))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> Mono.error(new RuntimeException("Invalid Parameter")))
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> Mono.error(new RuntimeException("Internal Server Error")))
                .bodyToMono(KakaoTokenResponse.class)
                .block();

        if (kakaoTokenResponse == null || kakaoTokenResponse.getAccessToken() == null) {
            throw new RuntimeException("Token response is empty");
        }

        String accessToken = kakaoTokenResponse.getAccessToken();
        String refreshToken = kakaoTokenResponse.getRefreshToken();
        String idToken = kakaoTokenResponse.getIdToken();


        Function<String, String> mask = token -> {
            if (token == null || token.length() < 8) return "****";
            return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
        };

        log.debug(" [Kakao Service] Access Token ------> {}", mask.apply(accessToken));
        log.debug(" [Kakao Service] Refresh Token ------> {}", mask.apply(refreshToken));
        log.debug(" [Kakao Service] Id Token ------> {}", mask.apply(idToken));
        log.debug(" [Kakao Service] Scope ------> {}", kakaoTokenResponse.getScope());

        return kakaoTokenResponse;
    }

    @Override
    public KakaoUserInfoResponse getUserInfo(String accessToken) {

        KakaoUserInfoResponse userInfo = WebClient.create(KAUTH_USER_URL_HOST)
                .get()
                .uri(uriBuilder -> uriBuilder
                        // .scheme("https")
                        .path("/v2/user/me")
                        .build(true))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> Mono.error(new RuntimeException("Invalid Parameter")))
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> Mono.error(new RuntimeException("Internal Server Error")))
                .bodyToMono(KakaoUserInfoResponse.class)
                .block();

        if (log.isDebugEnabled()) {
            log.info(" [Kakao Service] Auth ID ------> {}", userInfo.getId());
            log.info(" [Kakao Service] NickName ------> {}", userInfo.getKakaoAccount().getProfile().getNickName());
            log.info(" [Kakao Service] Id Token ------> {}", userInfo.getKakaoAccount().getProfile().getProfileImageUrl());
        }

        return userInfo;
    }
}
