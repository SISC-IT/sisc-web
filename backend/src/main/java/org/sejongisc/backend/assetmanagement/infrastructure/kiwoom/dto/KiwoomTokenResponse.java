package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KiwoomTokenResponse {
  @JsonProperty("token")
  private String token;

  @JsonProperty("expires_dt")
  private String expiresDt;

  @JsonProperty("token_type")
  private String tokenType;

  @JsonProperty("return_code")
  private String returnCode;

  @JsonProperty("return_msg")
  private String returnMsg;
}
