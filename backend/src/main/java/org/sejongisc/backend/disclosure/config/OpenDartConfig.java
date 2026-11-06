package org.sejongisc.backend.disclosure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(OpenDartProperties.class)
public class OpenDartConfig {
  @Bean
  WebClient openDartWebClient(WebClient.Builder builder, OpenDartProperties properties) {
    java.net.http.HttpClient jdkClient = java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(Math.max(properties.timeoutMs(), 1000)))
        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
        .build();
    return builder.clientConnector(new JdkClientHttpConnector(jdkClient))
        .baseUrl(properties.baseUrl())
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
        .build();
  }
}
