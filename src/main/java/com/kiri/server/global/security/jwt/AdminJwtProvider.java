package com.kiri.server.global.security.jwt;

import com.kiri.server.admin.auth.AdminProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

// 관리자 JWT. 앱 토큰과 type 클레임으로 구분해 서로의 API에서 거부된다.
@Component
public class AdminJwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ADMIN_ACCESS_TYPE = "admin_access";
    private static final String ADMIN_REFRESH_TYPE = "admin_refresh";

    private final AdminProperties adminProperties;
    private final SecretKey secretKey;

    public AdminJwtProvider(JwtProperties jwtProperties, AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long adminId) {
        return createToken(adminId, ADMIN_ACCESS_TYPE, adminProperties.accessTokenExpiration());
    }

    public String createRefreshToken(Long adminId) {
        return createToken(adminId, ADMIN_REFRESH_TYPE, adminProperties.refreshTokenExpiration());
    }

    public Long getAdminIdFromAccessToken(String token) {
        return getAdminId(token, ADMIN_ACCESS_TYPE);
    }

    public Long getAdminIdFromRefreshToken(String token) {
        return getAdminId(token, ADMIN_REFRESH_TYPE);
    }

    public long accessTokenExpiresInSeconds() {
        return adminProperties.accessTokenExpiration().toSeconds();
    }

    private Long getAdminId(String token, String expectedType) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        if (!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("올바른 토큰 타입이 아닙니다.");
        }
        return Long.valueOf(claims.getSubject());
    }

    private String createToken(Long adminId, String type, Duration expiration) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim(TOKEN_TYPE_CLAIM, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration.toMillis()))
                .signWith(secretKey)
                .compact();
    }
}
