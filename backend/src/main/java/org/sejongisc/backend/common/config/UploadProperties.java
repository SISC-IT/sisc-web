package org.sejongisc.backend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

  private String rootLocation;
  private String publicPathPrefix;
  private String publicBaseUrl;
  private DataSize imageMaxSize;
  private DataSize attachmentMaxSize;
  private DataSize videoMaxSize;
  private DataSize adminExcelMaxSize;
}
