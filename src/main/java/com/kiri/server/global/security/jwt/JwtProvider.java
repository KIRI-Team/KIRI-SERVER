package com.kiri.server.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

// JWT 생성 및 파싱
@Component
public class JwtProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, ACCESS_TYPE, jwtProperties.accessTokenExpiration());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, REFRESH_TYPE, jwtProperties.refreshTokenExpiration());
    }

    public Long getUserIdFromAccessToken(String token) {
        Claims claims=parseClaims(token);
        if(!ACCESS_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))){
            throw new JwtException("access 토큰이 아닙니다.");
        }
        return Long.valueOf(claims.getSubject());
    }

    private String createToken(Long userId, String type, Duration expiration) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration.toMillis()))
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
