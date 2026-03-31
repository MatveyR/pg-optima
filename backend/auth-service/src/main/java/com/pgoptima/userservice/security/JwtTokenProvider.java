package com.pgoptima.userservice.security;

import com.pgoptima.userservice.config.JwtConfigProperties;
import com.pgoptima.userservice.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtConfigProperties jwtConfig;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getAccessTokenExpirationMs());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuer(jwtConfig.getIssuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getRefreshTokenExpirationMs());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("type", "refresh")
                .issuer(jwtConfig.getIssuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }
    public String getEmailFromToken(String token) {
        Claims claims = validateAndParseToken(token);
        return claims.get("email", String.class);
    }

    public Claims validateAndParseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
            throw new JwtException("Token expired");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            throw new JwtException("Invalid token");
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = validateAndParseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public boolean isRefreshToken(String token) {
        Claims claims = validateAndParseToken(token);
        return "refresh".equals(claims.get("type"));
    }
}