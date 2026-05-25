package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KiwoomAccountResponse implements KiwoomResponse {
  @JsonProperty("acctNo")
  private String acctNo;

  @JsonProperty("return_code")
  private String returnCode;

  @JsonProperty("return_msg")
  private String returnMsg;

  @Override
  public String returnCode() {
    return returnCode;
  }

  @Override
  public String returnMsg() {
    return returnMsg;
  }
}
