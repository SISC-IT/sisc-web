package org.sejongisc.backend.common.config.security;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.controller.JwtAccessDeniedHandler;
import org.sejongisc.backend.common.exception.controller.JwtAuthenticationEntryPoint;
import org.sejongisc.backend.common.auth.filter.JwtAuthenticationFilter;
import org.sejongisc.backend.user.entity.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity       // @PreAuthorize 동작하려면 필요
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

//    private final GithubServiceImpl.CustomOAuth2UserService customOAuth2UserService;
//    private final GithubServiceImpl.CustomOidcUserService customOidcUserService;
//    private final GithubServiceImpl.OAuth2SuccessHandler oAuth2SuccessHandler;

    private final Environment env;

    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository() {
        return new HttpSessionOAuth2AuthorizationRequestRepository();
    }

    // 계층적 권한 설정
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
            .role(Role.SYSTEM_ADMIN.name()).implies(Role.PRESIDENT.name())
            .role(Role.PRESIDENT.name()).implies(Role.VICE_PRESIDENT.name())
            .role(Role.VICE_PRESIDENT.name()).implies(Role.TEAM_LEADER.name())
            .role(Role.TEAM_LEADER.name()).implies(Role.TEAM_MEMBER.name())
            // PENDING_MEMBER는 계층에 포함시키지 않음 (접근 불가)
            .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(request -> {
              String requestUri = request.getRequestURI();
              boolean isAdminRequest = requestUri.startsWith("/admin/dev");
              boolean isActuatorRequest = requestUri.startsWith("/actuator");
              return !isAdminRequest && !isActuatorRequest;
            })
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint) // 인증 실패 시 JSON 응답
                        .accessDeniedHandler(jwtAccessDeniedHandler)           // 인가 실패 시 JSON 응답
                )
//                .oauth2Login(oauth -> oauth
//                        .authorizationEndpoint(a ->
//                                a.authorizationRequestRepository(authorizationRequestRepository())
//                        )
//                        .userInfoEndpoint(u -> {
//                                    u.userService(customOAuth2UserService);  // kakao, github
//                                    u.oidcUserService(customOidcUserService);  //google
//                                }
//                        )
//                        .successHandler(oAuth2SuccessHandler)
//                        .failureHandler((req, res, ex) -> {
//                            if (isProd()) {
//                                res.sendRedirect("https://sjusisc.com/oauth/fail");
//                            }else if(isDev()){
//                                res.sendRedirect("https://sisc-web.duckdns.org/oauth/fail");
//                            }
//                            else {
//                                res.sendRedirect("http://localhost:5173/oauth/fail");
//                            }
//                        })
//                )

                .authorizeHttpRequests(auth -> {
                    // 모두 접근 가능한 API
                    auth.requestMatchers(SecurityConstants.WHITELIST_URLS).permitAll();
                    // 관리자 전용 API
                    auth.requestMatchers(SecurityConstants.ADMIN_ONLY_URLS).hasAnyRole(Role.PRESIDENT.name(), Role.SYSTEM_ADMIN.name());
                    // 일반 서비스 API (정회원 이상만 접근 가능, PENDING_MEMBER 자동 차단)
                    // RoleHierarchy 덕분에 TEAM_MEMBER만 적어도 상위 직급은 다 통과됨
                    auth.requestMatchers(SecurityConstants.MEMBER_ONLY_URLS).hasRole(Role.TEAM_MEMBER.name());

                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .anyRequest().authenticated();
                        //.anyRequest().permitAll();
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
                // TODO : OAUTH2를 쿠키에 저장 시 OR OAUTH2 쓰면 IF_REQUIRED 변경
                //.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        if(jwtAuthenticationFilter != null) {
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                env.getProperty("app.spring-api-url"),  // 스웨거 요청 막으려면 주석화 or 삭제
                env.getProperty("app.frontend-url")     // 환경변수에 해당하는 값 가져옴
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.addExposedHeader("Authorization");
        config.setMaxAge(3600L);    // 캐시 시간(초)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    private boolean isProd() {
        return List.of(env.getActiveProfiles()).contains("prod");
    }
    private boolean isDev() {
        return List.of(env.getActiveProfiles()).contains("dev");
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
