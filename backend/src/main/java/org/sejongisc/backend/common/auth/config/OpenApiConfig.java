package org.sejongisc.backend.common.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        servers = {
                @Server(url = "http://sisc-web.duckdns.org:8082", description = "dev Server"),
                @Server(url = "http://localhost:8080", description = "Local Server")
        }
)
public class OpenApiConfig {
    // 필요하면 추가적인 OpenAPI bean 설정 가능
}
