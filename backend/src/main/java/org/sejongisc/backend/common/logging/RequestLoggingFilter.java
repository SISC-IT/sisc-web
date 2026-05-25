package org.sejongisc.backend.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String MDC_REQUEST_ID = "requestId";
  private static final Pattern SENSITIVE_QUERY_PARAM_PATTERN = Pattern.compile(
      "(?i)(^|&)([^=&]*(token|secret|password|authorization|cookie|app[-_]?key|app[-_]?secret|code)[^=]*)=([^&]*)"
  );

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String requestId = resolveRequestId(request);
    long startedAt = System.currentTimeMillis();
    Exception failure = null;

    MDC.put(MDC_REQUEST_ID, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      filterChain.doFilter(request, response);
    } catch (Exception exception) {
      failure = exception;
      throw exception;
    } finally {
      long elapsedMs = System.currentTimeMillis() - startedAt;
      String requestTarget = resolveRequestTarget(request);
      String clientIp = resolveClientIp(request);
      String user = resolveCurrentUser();

      if (failure == null) {
        log.info(
            "HTTP {} {} -> status={} durationMs={} user={} ip={}",
            request.getMethod(),
            requestTarget,
            response.getStatus(),
            elapsedMs,
            user,
            clientIp
        );
      } else {
        log.warn(
            "HTTP {} {} -> status={} durationMs={} user={} ip={} exception={}",
            request.getMethod(),
            requestTarget,
            response.getStatus(),
            elapsedMs,
            user,
            clientIp,
            failure.getClass().getSimpleName()
        );
      }

      MDC.remove(MDC_REQUEST_ID);
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri != null && uri.startsWith("/actuator/health");
  }

  private String resolveRequestId(HttpServletRequest request) {
    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (StringUtils.hasText(requestId)) {
      return requestId.trim();
    }
    return UUID.randomUUID().toString();
  }

  private String resolveRequestTarget(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String queryString = request.getQueryString();
    if (!StringUtils.hasText(queryString)) {
      return uri;
    }
    return uri + "?" + sanitizeQueryString(queryString);
  }

  private String sanitizeQueryString(String queryString) {
    return SENSITIVE_QUERY_PARAM_PATTERN.matcher(queryString).replaceAll("$1$2=REDACTED");
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwardedFor)) {
      return forwardedFor.split(",")[0].trim();
    }
    String realIp = request.getHeader("X-Real-IP");
    if (StringUtils.hasText(realIp)) {
      return realIp.trim();
    }
    return request.getRemoteAddr();
  }

  private String resolveCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return "anonymous";
    }

    Object principal = authentication.getPrincipal();
    if (principal instanceof CustomUserDetails customUserDetails) {
      return String.valueOf(customUserDetails.getUserId());
    }

    String name = authentication.getName();
    if (StringUtils.hasText(name)) {
      return name;
    }
    return "authenticated";
  }
}
