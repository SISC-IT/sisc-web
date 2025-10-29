package org.sejongisc.backend.common.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

import java.util.*;
import javax.crypto.SecretKey;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetailsService;
import org.sejongisc.backend.user.entity.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtParser {
    private final CustomUserDetailsService customUserDetailsService;
    @Value("${jwt.secret}")
    private String rawSecretKey;

    private SecretKey secretKey;

    @Value("${jwt.expireDate.accessToken}")
    private long accessTokenValidityInMillis;

    @Value("${jwt.expireDate.refreshToken}")
    private long refreshTokenValidityInMillis;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(rawSecretKey);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // 토큰에서 사용자 ID 추출
    public UUID getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    // 토큰에서 사용자 role 추출
    public Role getRoleFromToken(String token) {
        Claims claims = parseClaims(token);
        String roleStr = claims.get("role", String.class);
        if (roleStr == null) {
            throw new JwtException("JWT에 role 클레임이 없습니다."); // 명확한 인증 실패 예외
        }
        try {
            return Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new JwtException("JWT의 role 클레임이 잘못되었습니다: " + roleStr);
        }
    }

    // 토큰 유효성 검증
    public boolean validationToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Authentication 객체 생성
    public UsernamePasswordAuthenticationToken getAuthentication(String token) {
        Claims claims = parseClaims(token);

        String roleStr = claims.get("role", String.class);
        if(roleStr == null) {
            throw new JwtException("JWT에 role 클레임이 없습니다.");
        }

        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new JwtException("JWT의 role 클레임이 잘못되었습니다.: " + roleStr);
        }

        Collection<? extends GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())); // "ROLE_TEAM_MEMBER"

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(claims.getSubject());
        return new UsernamePasswordAuthenticationToken(userDetails, "", authorities);
    }

    // Claims 파싱
    private Claims parseClaims(String token) {
        try{
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch(ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}