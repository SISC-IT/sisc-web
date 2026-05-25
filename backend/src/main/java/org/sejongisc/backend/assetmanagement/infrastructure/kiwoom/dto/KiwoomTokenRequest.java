package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KiwoomTokenRequest {
  @JsonProperty("grant_type")
  private String grantType;

  @JsonProperty("appkey")
  private String appKey;

  @JsonProperty("secretkey")
  private String appSecret;

  public static KiwoomTokenRequest of(String appKey, String appSecret) {
    return KiwoomTokenRequest.builder()
        .grantType("client_credentials")
        .appKey(appKey)
        .appSecret(appSecret)
        .build();
  }
}
