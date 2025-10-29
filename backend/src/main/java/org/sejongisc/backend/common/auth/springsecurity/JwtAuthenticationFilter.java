package org.sejongisc.backend.common.auth.springsecurity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.jwt.JwtParser;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.exception.ErrorResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final Optional<JwtParser> jwtParser;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> EXCLUDE_PATTERNS = List.of(
            "/user/signup",
            "/auth/login",
            "/auth/login/kakao",
            "/auth/login/google",
            "/auth/login/github",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui/index.html",
            "/swagger-resources/**",
            "/webjars/**"
    );

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 테스트 환경에서는 JwtParser가 없어도 그냥 통과시킴
        if (jwtParser.isEmpty()) {
            log.debug("JwtParser not found (likely test environment) → skipping JWT validation");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = resolveToken(request);
            JwtParser parser = jwtParser.get();

            if (token != null && parser.validationToken(token)) {
                UsernamePasswordAuthenticationToken authentication = parser.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Valid JWT token found for request: {}", request.getRequestURI());

                filterChain.doFilter(request, response);
            } else {
                throw new JwtException("Token is null or invalid");
            }

        } catch (JwtException e) {
            ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.INVALID_ACCESS_TOKEN);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(toJson(errorResponse));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDE_PATTERNS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String toJson(ErrorResponse errorResponse) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.writeValueAsString(errorResponse);
    }

}