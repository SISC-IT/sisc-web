package org.sejongisc.backend.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.auth.dto.KakaoTokenResponse;
import org.sejongisc.backend.auth.dto.KakaoUserInfoResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoServiceImplTest {

    private MockWebServer mockWebServer;
    private KakaoServiceImpl kakaoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // 테스트 대상 Service 초기화 (Mock 서버 주소로 주입)
        kakaoService = new KakaoServiceImpl("test-client-id", "http://localhost:8080/test-redirect", mockWebServer.url("/").toString(), mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getAccessTokenFromKakao_shouldReturnAccessToken() throws Exception {
        // given: 카카오 토큰 API mock 응답
        String mockJson = """
            {
              "token_type": "bearer",
              "access_token": "mock-access-token",
              "id_token": "mock-id-token",
              "expires_in": "3600",
              "refresh_token": "mock-refresh-token",
              "refresh_token_expires_in": "86400",
              "scope": "profile"
            }
            """;


        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJson)
                .addHeader("Content-Type", "application/json"));

        // when
        String accessToken = kakaoService.getAccessToken("test-code").getAccessToken();

        // then
        assertThat(accessToken).isEqualTo("mock-access-token");
    }

    @Test
    void getUserInfo_shouldReturnUserInfo() throws Exception {
        // given: 카카오 사용자 정보 API mock 응답
        String mockUserJson = """
            {
              "id": 12345,
              "kakao_account": {
                "profile": {
                  "nickname": "테스트닉네임",
                  "profile_image_url": "https://test.image/url"
                }
              }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockUserJson)
                .addHeader("Content-Type", "application/json"));

        // when
        KakaoUserInfoResponse result = kakaoService.getUserInfo("mock-access-token");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(12345L);
        assertThat(result.getKakaoAccount().getProfile().getNickName()).isEqualTo("테스트닉네임");
    }
}
