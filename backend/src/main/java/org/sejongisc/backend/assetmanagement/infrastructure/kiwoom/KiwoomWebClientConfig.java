package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class KiwoomWebClientConfig {
  @Bean
  public WebClient kiwoomWebClient(
      WebClient.Builder builder,
      @Value("${kiwoom.api.base-url:https://api.kiwoom.com}") String baseUrl,
      @Value("${kiwoom.api.timeout.connect-ms:3000}") int connectTimeoutMillis,
      @Value("${kiwoom.api.timeout.response-ms:5000}") long responseTimeoutMillis
  ) {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.max(100, connectTimeoutMillis))
        .responseTimeout(Duration.ofMillis(Math.max(1000, responseTimeoutMillis)));

    return builder
        .baseUrl(baseUrl)
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
}
