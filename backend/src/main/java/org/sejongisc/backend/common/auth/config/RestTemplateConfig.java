package org.sejongisc.backend.common.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis()); // 연결 타임아웃 3초
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());    // 응답 타임아웃 5초

        return new RestTemplate(factory);
    }

}
