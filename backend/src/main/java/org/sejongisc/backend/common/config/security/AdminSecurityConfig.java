package org.sejongisc.backend.common.config.security;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    // 1. 메모리 기반 관리자 계정 설정
    UserDetails adminUser = User.withUsername(adminUsername)
        .password(passwordEncoder.encode(adminPassword))
        .roles("ADMIN")
        .build();

    InMemoryUserDetailsManager userDetailsService = new InMemoryUserDetailsManager(adminUser);

    // 2. 관리자 전용 인증 프로바이더 설정
    DaoAuthenticationProvider adminAuthenticationProvider = new DaoAuthenticationProvider();
    adminAuthenticationProvider.setUserDetailsService(userDetailsService);
    adminAuthenticationProvider.setPasswordEncoder(passwordEncoder);

    http
        // 3. 적용 범위 지정: /admin/** 및 /actuator/** 경로에만 이 필터가 작동
        .securityMatcher(adminContextPath + "/**", "/actuator/**")
        .authenticationProvider(adminAuthenticationProvider)

        // 4. CSRF 예외: 클라이언트 서비스가 서버에 정보를 등록(POST)할 때 막히지 않도록 설정
        .csrf(csrf -> csrf.ignoringRequestMatchers(
            adminContextPath + "/instances",
            adminContextPath + "/instances/**"
        ))

        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

            // 5. 정적 리소스 및 파비콘: 로그인 없이도 브라우저가 읽을 수 있게 허용 (에러 방지)
            .requestMatchers(
                adminContextPath + "/assets/**",
                adminContextPath + "/login",
                "/favicon.ico",
                adminContextPath + "/favicon.ico"
            ).permitAll()

            // 6. 인스턴스 등록: 모니터링 대상 서버(Client)들의 자동 등록 허용
            .requestMatchers(adminContextPath + "/instances", adminContextPath + "/instances/**").permitAll()

            // 7. 헬스체크: 시스템 생존 여부 확인용 API 공개
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()

            // 8. 그 외 모든 관리자 페이지는 위에서 설정한 admin 계정 인증 필요
            .anyRequest().authenticated()
        )

        // 9. 로그인 방식: 웹 UI는 폼 로그인, API 통신은 Basic 인증 사용
        .formLogin(form -> form
            .loginPage(adminContextPath + "/login")
            .defaultSuccessUrl(adminContextPath + "/", true)
        )
        .httpBasic(Customizer.withDefaults());

    return http.build();
  }
}
