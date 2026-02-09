package org.sejongisc.backend.common.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.jwt.JwtParser;
import org.sejongisc.backend.common.config.security.SecurityConstants;
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

    private final JwtParser jwtParser;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        // 인증 제외 경로
        // 브라우저가 실제 요청 전에 서버에 보내는 CORS 예비 요청(Preflight 요청)은 OPTIONS 메서드 사용 (JWT 검사 제외)
        if (shouldNotFilter(request) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = resolveTokenFromHeader(request);
            if (token == null) {
                token = resolveTokenFromCookie(request);
            }

            if (token != null && jwtParser.validationToken(token) ) {
                UsernamePasswordAuthenticationToken authentication = jwtParser.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("SecurityContext에 인증 저장됨: {}", authentication.getName());
            } else {
                log.warn("토큰이 없거나 유효하지 않음");
            }
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            log.error("JWT validation failed: {}", e.getMessage(), e);
            sendErrorResponse(response, ErrorCode.INVALID_ACCESS_TOKEN);
            return; //  예외 시 여기서 중단
        }
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // 1) /actuator 전체 무조건 스킵
        if ("/actuator".equals(path) || path.startsWith("/actuator/") || path.startsWith("/admin")) {
            return true;
        }

        boolean excluded = Arrays.stream(SecurityConstants.WHITELIST_URLS)
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        // 어떤 요청이 필터 예외로 분류됐는지 콘솔에 표시
        log.debug("JwtFilter check path: {} → excluded={}", path, excluded);

        return excluded;
    }

    private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    private String resolveTokenFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String resolveTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        for (Cookie cookie : request.getCookies()) {
            if ("access".equals(cookie.getName())) {
                log.debug("쿠키에서 access token 추출됨");
                return cookie.getValue();
            }
        }
        return null;
    }
}