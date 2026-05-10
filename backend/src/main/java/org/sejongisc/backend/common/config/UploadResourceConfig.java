package org.sejongisc.backend.common.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

  @Value("${app.upload.root-location:${user.dir}/uploads}")
  private String uploadRootLocation;

  @Value("${app.upload.public-path-prefix:/uploads}")
  private String publicPathPrefix;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    Path uploadPath = Paths.get(uploadRootLocation).toAbsolutePath().normalize();
    registry.addResourceHandler(normalizePublicPathPrefix() + "/**")
        .addResourceLocations(uploadPath.toUri().toString());
  }

  private String normalizePublicPathPrefix() {
    if (!StringUtils.hasText(publicPathPrefix)) {
      return "/uploads";
    }
    return publicPathPrefix.startsWith("/") ? publicPathPrefix : "/" + publicPathPrefix;
  }
}
