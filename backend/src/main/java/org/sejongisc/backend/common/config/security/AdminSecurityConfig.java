package org.sejongisc.backend.common.config.security;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import org.sejongisc.backend.user.entity.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AdminSecurityConfig {

  private final AdminServerProperties adminServerProperties;

  public AdminSecurityConfig(AdminServerProperties adminServerProperties) {
    this.adminServerProperties = adminServerProperties;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
    String adminContextPath = adminServerProperties.getContextPath(); // /admin

    http
        .securityMatcher(adminContextPath + "/**", "/actuator/**")
        // 별도의 Provider 설정을 하지 않으면 기존에 등록된 UserDetailsService(DB 연동)를 사용함

        .csrf(csrf -> csrf.ignoringRequestMatchers(
            adminContextPath + "/instances",
            adminContextPath + "/instances/**"
        ))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(adminContextPath + "/assets/**").permitAll()
            .requestMatchers(adminContextPath + "/login").permitAll()
            .requestMatchers(adminContextPath + "/instances/**").permitAll()

            // ✅ 이 부분이 핵심: DB의 Role이 SYSTEM_ADMIN인 사용자만 허용
            .requestMatchers(adminContextPath + "/**").hasRole(Role.SYSTEM_ADMIN.name())
            .requestMatchers("/actuator/**").hasRole(Role.SYSTEM_ADMIN.name())

            .anyRequest().authenticated()
        )
        .formLogin(form -> form
            .loginPage(adminContextPath + "/login")
            .defaultSuccessUrl(adminContextPath + "/", true)
        )
        .httpBasic(Customizer.withDefaults());

    return http.build();
  }
}