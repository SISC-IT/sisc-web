package org.sejongisc.backend.common.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class UploadResourceConfig implements WebMvcConfigurer {

  private final UploadProperties uploadProperties;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    Path uploadPath = Paths.get(uploadProperties.getRootLocation()).toAbsolutePath().normalize();
    registry.addResourceHandler(normalizePublicPathPrefix() + "/**")
        .addResourceLocations(uploadPath.toUri().toString());
  }

  private String normalizePublicPathPrefix() {
    String publicPathPrefix = uploadProperties.getPublicPathPrefix();
    if (!StringUtils.hasText(publicPathPrefix)) {
      return "/uploads";
    }
    return publicPathPrefix.startsWith("/") ? publicPathPrefix : "/" + publicPathPrefix;
  }
}
