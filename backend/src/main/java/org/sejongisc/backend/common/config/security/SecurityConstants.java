package org.sejongisc.backend.common.config.security;

public class SecurityConstants {
    public static final String[] WHITELIST_URLS = {
            "/api/user/signup",
            "/api/auth/login",
            "/api/auth/login/**",
            "/api/auth/logout",
            "/api/auth/reissue",
            "/api/user/password/reset/**",
            "/api/email/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**",
            "/login/**",
            //"/oauth2/**",
            "/favicon.ico",
            "/api/user/password/reset/confirm",
            "/api/user/password/reset/send",
            "/actuator",
            "/actuator/**",
            "/error"
    };

    public static final String[] ADMIN_ONLY_URLS = {
            "/api/admin/**"
    };

    // TODO : URL 추가 필요
    public static final String[] MEMBER_ONLY_URLS = {
        "/api/user/**",
        "/api/user-bets/**",
        //"/api/board/**",
        //"/api/backtest/**",
        //"/api/quant-bot/**",
        //"/api/attendance/**"

    };

  public static final String[] ADMIN_URLS = {
      "/admin/**", "/actuator/**"
  };

  public static final String[] ADMIN_PUBLIC_URLS = {
      "/admin/assets/**",
      "/admin/login",
      "/favicon.ico",
      "/admin/favicon.ico",
      "/actuator/health",
      "/actuator/info"
  };
}