package org.sejongisc.backend.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.common.auth.dto.oauth.GoogleTokenResponse;
import org.sejongisc.backend.common.auth.dto.oauth.GoogleUserInfoResponse;
import org.sejongisc.backend.common.auth.service.oauth2.GoogleServiceImpl;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleServiceImplTest {

    private MockWebServer mockWebServer;
    private GoogleServiceImpl oauth2Service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // 테스트 대상 Service 초기화 (Mock 서버 주소로 주입)
        oauth2Service = new GoogleServiceImpl(
                "test-client-id",
                "test-client-secret",
                "http://localhost:8080/callback",
                mockWebServer.url("/").toString(),
                mockWebServer.url("/").toString()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getAccessTokenFromGoogle_shouldReturnAccessToken() throws Exception {
        // given: 구글 토큰 API mock 응답
        GoogleTokenResponse mockResponse = new GoogleTokenResponse();
        // ObjectMapper 직렬화 시 private 필드에 값 넣으려면 setter 필요하거나 생성자 필요.
        // 테스트에서는 리플렉션 없이 간단히 JSON 문자열을 직접 작성해도 됨.
        String mockJson = """
            {
              "access_token": "mock-access-token",
              "refresh_token": "mock-refresh-token",
              "id_token": "mock-id-token",
              "expires_in": 3600,
              "scope": "openid email profile",
              "token_type": "Bearer"
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJson)
                .addHeader("Content-Type", "application/json"));

        // when
        GoogleTokenResponse response = oauth2Service.getAccessToken("test-code");

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getIdToken()).isEqualTo("mock-id-token");
        assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token");
    }

    @Test
    void getUserInfo_shouldReturnUserInfo() throws Exception {
        // given: 구글 사용자 정보 API mock 응답
        String mockJson = """
            {
              "sub": "1234567890",
              "email": "testuser@gmail.com",
              "name": "테스트 유저",
              "picture": "http://example.com/avatar.png"
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJson)
                .addHeader("Content-Type", "application/json"));

        // when
        GoogleUserInfoResponse result = oauth2Service.getUserInfo("mock-access-token");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getSub()).isEqualTo("1234567890");
        assertThat(result.getEmail()).isEqualTo("testuser@gmail.com");
        assertThat(result.getName()).isEqualTo("테스트 유저");
        assertThat(result.getPicture()).isEqualTo("http://example.com/avatar.png");
    }
}
