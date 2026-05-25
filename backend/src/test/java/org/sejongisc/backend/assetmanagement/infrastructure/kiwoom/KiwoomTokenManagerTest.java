package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KiwoomTokenManagerTest {
  @Mock
  private KiwoomAuthClient authClient;

  @Test
  void reusesTokenUntilRefreshSkew() {
    when(authClient.issueAccessToken())
        .thenReturn(new KiwoomAccessToken("token-1", Instant.now().plus(Duration.ofHours(1))));
    KiwoomTokenManager tokenManager = new KiwoomTokenManager(authClient, 300);

    assertThat(tokenManager.getValidToken()).isEqualTo("token-1");
    assertThat(tokenManager.getValidToken()).isEqualTo("token-1");

    verify(authClient, times(1)).issueAccessToken();
  }

  @Test
  void invalidatesCurrentTokenAndReissues() {
    when(authClient.issueAccessToken())
        .thenReturn(new KiwoomAccessToken("token-1", Instant.now().plus(Duration.ofHours(1))))
        .thenReturn(new KiwoomAccessToken("token-2", Instant.now().plus(Duration.ofHours(1))));
    KiwoomTokenManager tokenManager = new KiwoomTokenManager(authClient, 300);

    String firstToken = tokenManager.getValidToken();
    tokenManager.invalidateIfCurrent(firstToken);

    assertThat(tokenManager.getValidToken()).isEqualTo("token-2");
    verify(authClient, times(2)).issueAccessToken();
  }
}
