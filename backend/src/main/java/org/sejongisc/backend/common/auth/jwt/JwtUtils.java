package org.sejongisc.backend.common.auth.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.service.CustomUserDetailsService;
import org.sejongisc.backend.user.entity.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";

    private final TokenEncryptor tokenEncryptor;
    private final CustomUserDetailsService customUserDetailsService;

    @Value("${jwt.secret}")
    private String rawSecretKey;

    @Value("${jwt.expireDate.accessToken}")
    private long accessTokenValidityInMillis;

    @Value("${jwt.expireDate.refreshToken}")
    private long refreshTokenValidityInMillis;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(rawSecretKey.getBytes(StandardCharsets.UTF_8));
    }

    // Access Token 생성
    public String createToken(UUID userId, Role role, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenValidityInMillis);

        return Jwts.builder()
                .setSubject(email)
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_ROLE, role.name())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // Refresh Token 생성 (AES-GCM 암호화 포함)
    public String createRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenValidityInMillis);

        String rawRefreshToken = Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();

        return tokenEncryptor.encrypt(rawRefreshToken);
    }

    // 토큰 만료일 조회
    public Date getExpiration(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
    }

    // 토큰에서 userId 추출 (uid 클레임 우선, 없으면 subject 사용 - RefreshToken 호환)
    public UUID getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        String userIdStr = claims.get(CLAIM_USER_ID, String.class);

        if (userIdStr == null || userIdStr.isBlank()) {
            userIdStr = claims.getSubject();
        }

        if (userIdStr == null || userIdStr.isBlank()) {
            throw new JwtException("JWT에 userId(uid/subject)가 없습니다.");
        }

        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("잘못된 userId 형식의 JWT입니다.");
        }
    }

    // 토큰에서 Role 추출
    public String getRoleFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get(CLAIM_ROLE, String.class);
    }

    // Authentication 객체 생성
    public UsernamePasswordAuthenticationToken getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String userId = claims.get(CLAIM_USER_ID, String.class);
        String roleStr = claims.get(CLAIM_ROLE, String.class);

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

        // TODO: 성능 고려해서 DB 조회 제거 고민
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(userId);
        log.debug("인증 객체 생성 완료");
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    // 토큰 유효성 검증
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

    // Claims 파싱 (만료된 토큰도 클레임 추출 가능)
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
}
