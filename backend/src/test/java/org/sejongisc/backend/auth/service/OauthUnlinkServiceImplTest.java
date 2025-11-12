package org.sejongisc.backend.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OauthUnlinkServiceImpl 단위 테스트")
class OauthUnlinkServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private OauthUnlinkServiceImpl oauthUnlinkService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // @Value 필드 수동 주입 (테스트용 더미 URL)
        oauthUnlinkService = new OauthUnlinkServiceImpl(restTemplate);
        setField("kakaoUnlinkUrl", "https://kakao.com/unlink");
        setField("googleUnlinkUrl", "https://google.com/revoke");
        setField("githubUnlinkUrl", "https://api.github.com/applications/{client_id}/grant");
        setField("githubClientId", "dummy-client-id");
        setField("githubClientSecret", "dummy-client-secret");
    }

    // 리플렉션으로 private @Value 필드 주입용
    private void setField(String fieldName, Object value) {
        try {
            var field = OauthUnlinkServiceImpl.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(oauthUnlinkService, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    @DisplayName("카카오 연결끊기 성공 테스트")
    void testUnlinkKakao_Success() {
        // given
        String accessToken = "test-kakao-token";
        ResponseEntity<String> mockResponse = new ResponseEntity<>("Success", HttpStatus.OK);

        when(restTemplate.exchange(
                eq("https://kakao.com/unlink"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class))
        ).thenReturn(mockResponse);

        // when
        oauthUnlinkService.unlinkKakao(accessToken);

        // then
        verify(restTemplate, times(1))
                .exchange(eq("https://kakao.com/unlink"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("카카오 연결끊기 실패 테스트 (예외 발생)")
    void testUnlinkKakao_Failure() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("API 오류"));

        assertDoesNotThrow(() -> oauthUnlinkService.unlinkKakao("invalid-token"));
    }


    @Test
    @DisplayName("구글 연결끊기 성공 테스트")
    void testUnlinkGoogle_Success() {
        String accessToken = "google-token";
        ResponseEntity<String> mockResponse = new ResponseEntity<>("ok", HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), isNull(), eq(String.class)))
                .thenReturn(mockResponse);

        oauthUnlinkService.unlinkGoogle(accessToken);

        verify(restTemplate, times(1))
                .postForEntity(contains("https://google.com/revoke?token=" + accessToken), isNull(), eq(String.class));
    }

    @Test
    @DisplayName("구글 연결끊기 실패 테스트")
    void testUnlinkGoogle_Failure() {
        when(restTemplate.postForEntity(anyString(), isNull(), eq(String.class)))
                .thenThrow(new RuntimeException("Google API 실패"));

        assertDoesNotThrow(() -> oauthUnlinkService.unlinkGoogle("bad-token"));
    }


    @Test
    @DisplayName("깃허브 연결끊기 성공 테스트")
    void testUnlinkGithub_Success() {
        ResponseEntity<String> mockResponse = new ResponseEntity<>("ok", HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(String.class))
        ).thenReturn(mockResponse);

        oauthUnlinkService.unlinkGithub("gh-token");

        verify(restTemplate, times(1))
                .exchange(contains("https://api.github.com/applications/dummy-client-id/grant"),
                        eq(HttpMethod.DELETE),
                        any(HttpEntity.class),
                        eq(String.class));
    }

    @Test
    @DisplayName("깃허브 연결끊기 실패 테스트")
    void testUnlinkGithub_Failure() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("GitHub API 실패"));

        assertDoesNotThrow(() -> oauthUnlinkService.unlinkGithub("gh-bad-token"));
    }
}
