package org.sejongisc.backend.common.config.swagger;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        servers = {
                @Server(url = "/", description = "Default Server URL"), // 현재 접속한 IP를 자동으로 사용
                @Server(url = "http://localhost:8080", description = "Local Server")
        }
)
public class OpenApiConfig {
    // 필요하면 추가적인 OpenAPI bean 설정 가능
}
