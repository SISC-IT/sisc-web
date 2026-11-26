package org.sejongisc.backend.disclosure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opendart.api")
public record OpenDartProperties(String baseUrl, String key, long timeoutMs) {
}
