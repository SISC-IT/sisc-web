package org.sejongisc.backend.common.auth.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetailsService;
import org.sejongisc.backend.user.entity.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtParser {
    private final CustomUserDetailsService customUserDetailsService;
    @Value("${jwt.secret}")
    private String rawSecretKey;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(rawSecretKey);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // 토큰 유효성 검사
    public boolean validationToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            log.info("Token validation success");
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    // Authentication 생성
    public UsernamePasswordAuthenticationToken getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String userId = claims.get("uid", String.class);

        String roleStr = claims.get("role", String.class);
        if (roleStr == null) {
            throw new JwtException("JWT에 role 클레임이 없습니다.");
        }

        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new JwtException("JWT의 role 클레임이 잘못되었습니다.: " + roleStr);
        }

        if (userId == null) {
                      throw new JwtException("JWT에 userId(uid)가 없습니다.");
        }

        // DB에서 다시 유저를 불러오기 (CustomUserDetailsService 사용)
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(userId);

        log.debug("인증 객체 생성 완료");
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    }

    // Claims 파싱
    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        String userIdStr = claims.get("uid", String.class);

        // uid 클레임이 없을 경우 subject로 대체 (RefreshToken 호환)
        if (userIdStr == null || userIdStr.isBlank()) {
            userIdStr = claims.getSubject();
        }

        // 여전히 없거나 비어 있으면 명시적 예외 처리
        if (userIdStr == null || userIdStr.isBlank()) {
            throw new JwtException("JWT에 userId(uid/subject)가 없습니다.");
        }

        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("잘못된 userId 형식의 JWT입니다.");
        }
    }
}
