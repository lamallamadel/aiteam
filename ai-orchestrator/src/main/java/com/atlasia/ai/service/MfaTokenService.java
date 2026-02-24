package com.atlasia.ai.service;

import com.atlasia.ai.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MfaTokenService {

    private static final Logger logger = LoggerFactory.getLogger(MfaTokenService.class);
    private static final int MFA_TOKEN_EXPIRATION_MINUTES = 5;

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public MfaTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        if (jwtProperties.getSecretKey() == null || jwtProperties.getSecretKey().isEmpty()) {
            throw new IllegalStateException("JWT secret key must be configured");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    public String generateMfaToken(UUID userId, String username) {
        Instant now = Instant.now();
        Instant expiration = now.plus(MFA_TOKEN_EXPIRATION_MINUTES, ChronoUnit.MINUTES);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("type", "mfa");

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();
    }

    public boolean validateMfaToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String type = claims.get("type", String.class);
            return "mfa".equals(type);
        } catch (Exception e) {
            logger.warn("Invalid MFA token: {}", e.getMessage());
            return false;
        }
    }

    public UUID extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userIdStr = claims.get("userId", String.class);
            return UUID.fromString(userIdStr);
        } catch (Exception e) {
            logger.error("Failed to extract user ID from MFA token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid MFA token", e);
        }
    }

    public String extractUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return claims.getSubject();
        } catch (Exception e) {
            logger.error("Failed to extract username from MFA token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid MFA token", e);
        }
    }
}
