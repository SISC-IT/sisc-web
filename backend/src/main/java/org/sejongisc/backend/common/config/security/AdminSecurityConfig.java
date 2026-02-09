package org.sejongisc.backend.common.config.security;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AdminSecurityConfig {

  private final AdminServerProperties adminServerProperties;
  private final PasswordEncoder passwordEncoder;

  @Value("${spring.security.user.name}")
  private String adminUsername;

  @Value("${spring.security.user.password}")
  private String adminPassword;

  public AdminSecurityConfig(AdminServerProperties adminServerProperties, PasswordEncoder passwordEncoder) {
    this.adminServerProperties = adminServerProperties;
    this.passwordEncoder = passwordEncoder;
  }

  @Bean
  @Order(1) // 1순위로 체크: /admin 및 /actuator 경로는 이 설정이 우선 적용됨
  public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
    String adminContextPath = adminServerProperties.getContextPath();

    // 관리자 계정 설정
    UserDetails adminUser = User.withUsername(adminUsername)
        .password(passwordEncoder.encode(adminPassword))
        .roles("ADMIN")
        .build();

    InMemoryUserDetailsManager userDetailsService = new InMemoryUserDetailsManager(adminUser);

    // 관리자 전용 인증 프로바이더 설정
    DaoAuthenticationProvider adminAuthenticationProvider = new DaoAuthenticationProvider();
    adminAuthenticationProvider.setUserDetailsService(userDetailsService);
    adminAuthenticationProvider.setPasswordEncoder(passwordEncoder);

    http
        // 매칭 범위에 /actuator/** 를 명시적으로 포함
        .securityMatcher(adminContextPath + "/**", "/actuator/**")
        .authenticationProvider(adminAuthenticationProvider)
        .csrf(csrf -> csrf.ignoringRequestMatchers(
            adminContextPath + "/instances",
            adminContextPath + "/instances/**",
            "/actuator/**"
        ))
        .authorizeHttpRequests(auth -> auth
            // 누구나 볼 수 있는 정적 리소스 및 헬스체크
            .requestMatchers(
                adminContextPath + "/assets/**",
                adminContextPath + "/login",
                "/favicon.ico",
                adminContextPath + "/favicon.ico",
                "/actuator/health",
                "/actuator/info"
            ).permitAll()

            // SBA 클라이언트 등록 엔드포인트 보호
            .requestMatchers(adminContextPath + "/instances", adminContextPath + "/instances/**").authenticated()

            // 나머지 모든 /admin/** 및 /actuator/** (metrics, env, log 등)는 관리자 인증 필수
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
