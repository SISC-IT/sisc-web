package org.sejongisc.backend.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.user.dto.GithubTokenResponse;
import org.sejongisc.backend.user.dto.GithubUserInfoResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GithubServiceImplTest {

    private MockWebServer mockWebServer;
    private GithubServiceImpl githubService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // ✅ 테스트용 생성자 사용
        githubService = new GithubServiceImpl(
                "test-client-id",
                "test-client-secret",
                mockWebServer.url("/").toString(),
                mockWebServer.url("/").toString()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getAccessTokenFromGithub_shouldReturnAccessToken() throws Exception {
        String mockJson = """
            {
              "access_token": "mock-access-token",
              "token_type": "bearer",
              "scope": "read:user"
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJson)
                .addHeader("Content-Type", "application/json"));

        GithubTokenResponse response = githubService.getAccessTokenFromGithub("test-code");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
    }

    @Test
    void getUserInfo_shouldReturnUserInfo() throws Exception {
        String mockJson = """
            {
              "id": 12345,
              "login": "testlogin",
              "name": "테스트유저",
              "email": "test@example.com",
              "avatar_url": "http://example.com/avatar.png"
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJson)
                .addHeader("Content-Type", "application/json"));

        GithubUserInfoResponse userInfo = githubService.getUserInfo("mock-token");

        assertThat(userInfo).isNotNull();
        assertThat(userInfo.getLogin()).isEqualTo("testlogin");
        assertThat(userInfo.getEmail()).isEqualTo("test@example.com");
    }
}
