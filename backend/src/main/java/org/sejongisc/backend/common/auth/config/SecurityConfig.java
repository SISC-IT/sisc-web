package org.sejongisc.backend.common.auth.config;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.jwt.JwtAccessDeniedHandler;
import org.sejongisc.backend.common.auth.jwt.JwtAuthenticationEntryPoint;
import org.sejongisc.backend.common.auth.springsecurity.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    private final Environment env;

    private boolean isProd() {
        return List.of(env.getActiveProfiles()).contains("prod");
    }

    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository() {
        return new HttpSessionOAuth2AuthorizationRequestRepository();
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint) // 인증 실패 시 JSON 응답
                        .accessDeniedHandler(jwtAccessDeniedHandler)           // 인가 실패 시 JSON 응답
                )
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(a ->
                                a.authorizationRequestRepository(authorizationRequestRepository())
                        )
                        .userInfoEndpoint(u -> {
                                    u.userService(customOAuth2UserService);  // kakao, github
                                    u.oidcUserService(customOidcUserService);  //google
                                }
                        )
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler((req, res, ex) -> {
                            if (isProd()) {
                                res.sendRedirect("https://sisc-web.duckdns.org/oauth/fail");
                            } else {
                                res.sendRedirect("http://localhost:5173/oauth/fail");
                            }
                        })
                )

                .authorizeHttpRequests(auth -> {
                    auth
                            .requestMatchers(
                                    "/api/auth/signup",
                                    "/api/auth/login",
                                    "/api/auth/login/**",
                                    "/actuator",
                                    "/actuator/**",
                                    "/api/auth/logout",
                                    "/api/auth/reissue",
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",

                                    "/api/user/id/find",
                                    "/api/user/password/reset/**",

                                    "/api/email/**",
                                    "/swagger-resources/**",
                                    "/webjars/**",
                                    "/login/**",
                                    "/oauth2/**"
                            ).permitAll();

                            auth.requestMatchers("/api/user/**").authenticated();

                            auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
//                            .anyRequest().authenticated();
                            .anyRequest().permitAll();
                })
                //.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

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
                "https://sisc-web.duckdns.org"
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


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
