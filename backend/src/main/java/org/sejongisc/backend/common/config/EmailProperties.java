package org.sejongisc.backend.common.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "email")
@Getter
@Setter
@Configuration
public class EmailProperties {
  private Duration codeExpire;
  private Duration verifiedExpire;
  private KeyPrefix keyPrefix;
  private Code code;

  @Setter
  @Getter
  public static class KeyPrefix {
    private String verify;
    private String verified;
  }

  @Setter
  @Getter
  public static class Code {
    private String charset;   // 문자 세트
    private int length;       // 기본 길이
  }

}
