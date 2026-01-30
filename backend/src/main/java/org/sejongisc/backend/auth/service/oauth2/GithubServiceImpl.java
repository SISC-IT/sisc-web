package org.sejongisc.backend.auth.service.oauth2;

import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.dto.GithubTokenResponse;
import org.sejongisc.backend.auth.dto.GithubUserInfoResponse;
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
@Service("GITHUB")
public class GithubServiceImpl implements Oauth2Service<GithubTokenResponse, GithubUserInfoResponse> {

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
    public GithubTokenResponse getAccessToken(String code) {
        GithubTokenResponse tokenResponse = WebClient.create(TOKEN_URL).post()
                .uri(uriBuilder -> uriBuilder.build(true))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(BodyInserters.fromFormData("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("code", code))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        clientResponse -> Mono.error(new RuntimeException("Invalid Parameter")))
                .onStatus(HttpStatusCode::is5xxServerError,
                        clientResponse -> Mono.error(new RuntimeException("Internal Server Error")))
                .bodyToMono(GithubTokenResponse.class)
                .block();

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new RuntimeException("Token response is empty");
        }

        Function<String, String> mask = token -> {
            if(token == null || token.length() < 8) return "****";
            return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
        };

        log.debug(" [Github Service] Access Token ------> {}", mask.apply(tokenResponse.getAccessToken()));
        log.debug(" [Github Service] Scope        ------> {}", mask.apply(tokenResponse.getScope()));

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

        if (userInfo == null) {
            throw new RuntimeException("UserInfo response is empty");
        }

        if (log.isDebugEnabled()) {
            log.debug(" [Github Service] ID    ------> {}", userInfo.getId());
            log.debug(" [Github Service] Login ------> {}", userInfo.getLogin());
            log.debug(" [Github Service] Name  ------> {}", userInfo.getName());
        }

        return userInfo;
    }
}
