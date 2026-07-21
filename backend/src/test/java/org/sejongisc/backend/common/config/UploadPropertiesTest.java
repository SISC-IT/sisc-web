package org.sejongisc.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

class UploadPropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
      .withUserConfiguration(UploadProperties.class);

  private final ApplicationContextRunner yamlContextRunner = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
      .withUserConfiguration(UploadProperties.class);

  @Test
  @DisplayName("업로드 설정 평면 키 바인딩")
  void uploadProperties_flatKeys_bind() {
    contextRunner
        .withPropertyValues(
            "app.upload.root-location=/tmp/sisc-uploads",
            "app.upload.public-path-prefix=/uploads",
            "app.upload.public-base-url=https://api.example.com",
            "app.upload.image-max-size=10MB",
            "app.upload.attachment-max-size=30MB",
            "app.upload.video-max-size=100MB",
            "app.upload.admin-excel-max-size=5MB"
        )
        .run(context -> {
          UploadProperties properties = context.getBean(UploadProperties.class);

          assertThat(properties.getRootLocation()).isEqualTo("/tmp/sisc-uploads");
          assertThat(properties.getPublicPathPrefix()).isEqualTo("/uploads");
          assertThat(properties.getPublicBaseUrl()).isEqualTo("https://api.example.com");
          assertThat(properties.getImageMaxSize()).isEqualTo(DataSize.ofMegabytes(10));
          assertThat(properties.getAttachmentMaxSize()).isEqualTo(DataSize.ofMegabytes(30));
          assertThat(properties.getVideoMaxSize()).isEqualTo(DataSize.ofMegabytes(100));
          assertThat(properties.getAdminExcelMaxSize()).isEqualTo(DataSize.ofMegabytes(5));
        });
  }

  @Test
  @DisplayName("업로드 yml 기본값과 기존 API URL fallback 바인딩")
  void uploadProperties_yamlDefaults_bind() {
    yamlContextRunner
        .withPropertyValues(
            "spring.profiles.active=test",
            "app.spring-api-url=https://legacy-api.example.com"
        )
        .run(context -> {
          UploadProperties properties = context.getBean(UploadProperties.class);

          assertThat(properties.getRootLocation())
              .isEqualTo(System.getProperty("user.dir") + "/uploads");
          assertThat(properties.getPublicPathPrefix()).isEqualTo("/uploads");
          assertThat(properties.getPublicBaseUrl()).isEqualTo("https://legacy-api.example.com");
          assertThat(properties.getImageMaxSize()).isEqualTo(DataSize.ofMegabytes(10));
          assertThat(properties.getAttachmentMaxSize()).isEqualTo(DataSize.ofMegabytes(30));
          assertThat(properties.getVideoMaxSize()).isEqualTo(DataSize.ofMegabytes(100));
          assertThat(properties.getAdminExcelMaxSize()).isEqualTo(DataSize.ofMegabytes(5));
        });
  }
}
