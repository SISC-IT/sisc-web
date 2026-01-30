package org.sejongisc.backend.auth.service.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.auth.service.oauth2.exception.OauthUnlinkException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OauthUnlinkServiceImpl implements OauthUnlinkService {

    private final RestTemplate restTemplate;

    @Value("${kakao.unlink-url}")
    private String kakaoUnlinkUrl;

    @Value("${google.unlink-url}")
    private String googleUnlinkUrl;

    @Value("${github.unlink-url}")
    private String githubUnlinkUrl;

    @Value("${github.client.id}")
    private String githubClientId;

    @Value("${github.client.secret}")
    private String githubClientSecret;

    // 카카오 연결 끊기
    @Override
    public void unlinkKakao(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Access token은 필수입니다.");
        }

        try{
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(kakaoUnlinkUrl, HttpMethod.POST, request, String.class);
            log.info("Kakao Unlink 성공: {}", response.getBody());
        }catch (Exception e){
            log.warn("Kakao Unlink 실패: {}", e.getMessage());
            throw new OauthUnlinkException("Kakao 연동 해제 실패", e);
        }
    }

    @Override
    public void unlinkGoogle(String accessToken) throws OauthUnlinkException{
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Access token은 필수입니다.");
        }

        try{
            String url = UriComponentsBuilder.fromHttpUrl(googleUnlinkUrl)
                    .queryParam("token", accessToken)
                    .build()
                    .toUriString();

            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, null, String.class);
            log.info("Google unlink 성공: {}", response.getBody());
        } catch (Exception e) {
            log.warn("Google unlink 실패: {}", e.getMessage());
            throw new OauthUnlinkException("Google 연동 해제 실패", e);
        }
    }

    @Override
    public void unlinkGithub(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Access token은 필수입니다.");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(githubClientId, githubClientSecret);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = Map.of("access_token", accessToken);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    githubUnlinkUrl.replace("{client_id}", githubClientId),
                    HttpMethod.DELETE,
                    request,
                    String.class
            );
            log.info("GitHub unlink 성공: {}", response.getBody());
        } catch (Exception e) {
            log.warn("GitHub unlink 실패: {}", e.getMessage());
            throw new OauthUnlinkException("GitHub 연동 해제 실패", e);
        }
    }
}
