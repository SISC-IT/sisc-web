package org.sejongisc.backend.attendance.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 관리자 전용 엔드포인트들 (구체적인 패턴부터 먼저)
                        .requestMatchers(HttpMethod.POST, "/api/attendance/sessions/*/attendances/*").hasAnyRole("PRESIDENT", "VICE_PRESIDENT")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/sessions/*/attendances").hasAnyRole("PRESIDENT", "VICE_PRESIDENT")
                        .requestMatchers(HttpMethod.POST, "/api/attendance/sessions").hasAnyRole("PRESIDENT", "VICE_PRESIDENT")
                        .requestMatchers(HttpMethod.PUT, "/api/attendance/sessions/*").hasAnyRole("PRESIDENT", "VICE_PRESIDENT")
                        .requestMatchers(HttpMethod.DELETE, "/api/attendance/sessions/*").hasAnyRole("PRESIDENT", "VICE_PRESIDENT")
                        .requestMatchers("/api/attendance/sessions/*/activate").hasAnyRole("PRESIDENT", "VICE_PRESIDENT")
                        .requestMatchers("/api/attendance/sessions/*/close").hasAnyRole("PRESIDENT", "VICE_PRESIDENT")
                        .requestMatchers("/api/attendance/sessions/status/*").hasAnyRole("PRESIDENT", "VICE_PRESIDENT")
                        // 나머지는 모든 인증된 사용자
                        .anyRequest().authenticated()
                ).build();
    }
}
