package org.sejongisc.backend.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.auth.dto.KakaoTokenResponse;
import org.sejongisc.backend.auth.dto.KakaoUserInfoResponse;
import org.sejongisc.backend.auth.service.KakaoServiceImpl;

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
        kakaoService = new KakaoServiceImpl("test-client-id", mockWebServer.url("/").toString(), mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getAccessTokenFromKakao_shouldReturnAccessToken() throws Exception {
        // given: 카카오 토큰 API mock 응답
        KakaoTokenResponse mockResponse = new KakaoTokenResponse();
        mockResponse.accessToken = "mock-access-token";
        mockResponse.refreshToken = "mock-refresh-token";
        mockResponse.idToken = "mock-id-token";
        mockResponse.scope = "profile";

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));

        // when
        String accessToken = kakaoService.getAccessTokenFromKakao("test-code").getAccessToken();

        // then
        assertThat(accessToken).isEqualTo("mock-access-token");
    }

    @Test
    void getUserInfo_shouldReturnUserInfo() throws Exception {
        // given: 카카오 사용자 정보 API mock 응답
        KakaoUserInfoResponse mockUserInfo = new KakaoUserInfoResponse();
        mockUserInfo.id = 12345L;

        KakaoUserInfoResponse.KakaoAccount account = mockUserInfo.new KakaoAccount();
        KakaoUserInfoResponse.KakaoAccount.Profile profile = account.new Profile();
        profile.nickName = "테스트닉네임";
        account.profile = profile;
        mockUserInfo.kakaoAccount = account;

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockUserInfo))
                .addHeader("Content-Type", "application/json"));

        // when
        KakaoUserInfoResponse result = kakaoService.getUserInfo("mock-access-token");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(12345L);
        assertThat(result.getKakaoAccount().getProfile().getNickName()).isEqualTo("테스트닉네임");
    }
}
