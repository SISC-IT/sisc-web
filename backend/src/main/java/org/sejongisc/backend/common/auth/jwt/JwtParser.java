package org.sejongisc.backend.common.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

import java.util.*;
import javax.crypto.SecretKey;

import org.sejongisc.backend.user.entity.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtParser {
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
        return Role.valueOf(roleStr);
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

        Role role = Role.valueOf(claims.get("role", String.class));
        if (role == null) {
            throw new RuntimeException("role is null");
        }

        Collection<? extends GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + role)); // "ROLE_TEAM_MEMBER"


        UserDetails userDetails = new User(claims.getSubject(), "", authorities);
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