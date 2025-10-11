package org.sejongisc.backend.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.dto.GithubTokenResponse;
import org.sejongisc.backend.auth.dto.GithubUserInfoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class GithubServiceImpl implements GithubService {

    private final String clientId;
    private final String clientSecret;

    private final String TOKEN_URL;
    private final String USERINFO_URL;

    @Autowired
    public GithubServiceImpl(
            @Value("${github.client.id}") String clientId,
            @Value("${github.client.secret}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.TOKEN_URL = "https://github.com/login/oauth/access_token";
        this.USERINFO_URL = "https://api.github.com/user";
    }

    // ✅ 테스트용 생성자
    public GithubServiceImpl(String clientId, String clientSecret,
                                String tokenUrl, String userInfoUrl) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.TOKEN_URL = tokenUrl;
        this.USERINFO_URL = userInfoUrl;
    }

    @Override
    public GithubTokenResponse getAccessTokenFromGithub(String code) {
        GithubTokenResponse tokenResponse = WebClient.create(TOKEN_URL).post()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("client_id", clientId)
                        .queryParam("client_secret", clientSecret)
                        .queryParam("code", code)
                        .build(true))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        clientResponse -> Mono.error(new RuntimeException("Invalid Parameter")))
                .onStatus(HttpStatusCode::is5xxServerError,
                        clientResponse -> Mono.error(new RuntimeException("Internal Server Error")))
                .bodyToMono(GithubTokenResponse.class)
                .block();

        log.info(" [Github Service] Access Token ------> {}", tokenResponse.getAccessToken());
        log.info(" [Github Service] Scope        ------> {}", tokenResponse.getScope());

        return tokenResponse;
    }

    @Override
    public GithubUserInfoResponse getUserInfo(String accessToken) {
        GithubUserInfoResponse userInfo = WebClient.create(USERINFO_URL).get()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        clientResponse -> Mono.error(new RuntimeException("Invalid Parameter")))
                .onStatus(HttpStatusCode::is5xxServerError,
                        clientResponse -> Mono.error(new RuntimeException("Internal Server Error")))
                .bodyToMono(GithubUserInfoResponse.class)
                .block();

        log.info(" [Github Service] ID    ------> {}", userInfo.getId());
        log.info(" [Github Service] Login ------> {}", userInfo.getLogin());
        log.info(" [Github Service] Email ------> {}", userInfo.getEmail());
        log.info(" [Github Service] Name  ------> {}", userInfo.getName());

        return userInfo;
    }
}
