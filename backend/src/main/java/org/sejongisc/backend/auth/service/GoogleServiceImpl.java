package org.sejongisc.backend.auth.service;

import io.netty.handler.codec.http.HttpHeaderValues;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.dto.GoogleTokenResponse;
import org.sejongisc.backend.auth.dto.GoogleUserInfoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@Slf4j
@Service("GOOGLE")
public class GoogleServiceImpl implements Oauth2Service<GoogleTokenResponse, GoogleUserInfoResponse> {

    private final String GOOGLE_TOKEN_URL_HOST;
    private final String GOOGLE_USERINFO_URL_HOST;

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    @Autowired
    public GoogleServiceImpl(
            @Value("${google.client.id}") String clientId,
            @Value("${google.client.secret}") String clientSecret,
            @Value("${google.redirect.uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;

        GOOGLE_TOKEN_URL_HOST = "https://oauth2.googleapis.com";
        GOOGLE_USERINFO_URL_HOST = "https://openidconnect.googleapis.com";
    }

    // 테스트용 생성자 (MockWebServer 등)
    public GoogleServiceImpl(String clientId, String clientSecret, String redirectUri,
                             String GOOGLE_TOKEN_URL_HOST, String GOOGLE_USERINFO_URL_HOST) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.GOOGLE_TOKEN_URL_HOST = GOOGLE_TOKEN_URL_HOST.replace("https://", "http://");
        this.GOOGLE_USERINFO_URL_HOST = GOOGLE_USERINFO_URL_HOST.replace("https://", "http://");
    }

    @Override
    public GoogleTokenResponse getAccessToken(String code) {

        GoogleTokenResponse tokenResponse = WebClient.create(GOOGLE_TOKEN_URL_HOST).post()
                .uri(uriBuilder -> uriBuilder.path("/token").build(true))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("redirect_uri", redirectUri)
                        .with("code", code))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        clientResponse -> Mono.error(new RuntimeException("Invalid Parameter")))
                .onStatus(HttpStatusCode::is5xxServerError,
                        clientResponse -> Mono.error(new RuntimeException("Internal Server Error")))
                .bodyToMono(GoogleTokenResponse.class)
                .block();

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new RuntimeException("Token response is empty");
        }

        Function<String, String> mask = token -> {
            if (token == null || token.length() < 8) return "****";
            return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
        };

        log.debug(" [Google Service] Access Token  ------> {}", mask.apply(tokenResponse.getAccessToken()));
        log.debug(" [Google Service] Refresh Token ------> {}", mask.apply(tokenResponse.getRefreshToken()));
        log.debug(" [Google Service] Id Token      ------> {}", mask.apply(tokenResponse.getIdToken()));
        log.debug(" [Google Service] Scope         ------> {}", tokenResponse.getScope());

        return tokenResponse;
    }

    @Override
    public GoogleUserInfoResponse getUserInfo(String accessToken) {

        GoogleUserInfoResponse userInfo = WebClient.create(GOOGLE_USERINFO_URL_HOST)
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/userinfo")
                        .build(true))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        clientResponse -> Mono.error(new RuntimeException("Invalid Parameter")))
                .onStatus(HttpStatusCode::is5xxServerError,
                        clientResponse -> Mono.error(new RuntimeException("Internal Server Error")))
                .bodyToMono(GoogleUserInfoResponse.class)
                .block();

        if(userInfo == null) {
            throw new RuntimeException("UserInfo response is empty");
        }

        log.info(" [Google Service] Sub(ID)   ------> {}", userInfo.getSub());
        log.info(" [Google Service] Email     ------> {}", userInfo.getEmail());
        log.info(" [Google Service] Name      ------> {}", userInfo.getName());
        log.info(" [Google Service] Picture   ------> {}", userInfo.getPicture());

        return userInfo;
    }
}