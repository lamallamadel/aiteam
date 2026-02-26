package com.atlasia.ai.service;

import com.atlasia.ai.config.JwtProperties;
import com.atlasia.ai.model.PermissionEntity;
import com.atlasia.ai.model.RoleEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    
    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;
    private final OrchestratorMetrics metrics;

    public JwtService(JwtProperties jwtProperties, OrchestratorMetrics metrics) {
        this.jwtProperties = jwtProperties;
        this.metrics = metrics;
        if (jwtProperties.getSecretKey() == null || jwtProperties.getSecretKey().isEmpty()) {
            throw new IllegalStateException("JWT secret key must be configured");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(jwtProperties.getAccessTokenExpirationMinutes(), ChronoUnit.MINUTES);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        
        var roles = user.getRoles().stream()
                .map(RoleEntity::getName)
                .collect(Collectors.toList());
        claims.put("roles", roles);
        
        var permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(PermissionEntity::getAuthority)
                .collect(Collectors.toSet());
        
        var userPermissions = user.getPermissions().stream()
                .map(PermissionEntity::getAuthority)
                .collect(Collectors.toSet());
        
        var allPermissions = Stream.concat(permissions.stream(), userPermissions.stream())
                .collect(Collectors.toList());
        
        claims.put("permissions", allPermissions);

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(jwtProperties.getRefreshTokenExpirationDays(), ChronoUnit.DAYS);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("type", "refresh");

        try {
            String token = Jwts.builder()
                    .claims(claims)
                    .subject(user.getUsername())
                    .issuer(jwtProperties.getIssuer())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiration))
                    .id(UUID.randomUUID().toString())
                    .signWith(secretKey, Jwts.SIG.HS512)
                    .compact();
            metrics.recordJwtTokenRefresh();
            return token;
        } catch (Exception e) {
            metrics.recordJwtTokenRefreshFailure();
            throw e;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SignatureException e) {
            logger.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.warn("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public Claims extractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            logger.error("Failed to extract claims from JWT: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        String userIdStr = extractClaims(token).get("userId", String.class);
        return UUID.fromString(userIdStr);
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public Instant getTokenExpiration(String token) {
        return extractClaims(token).getExpiration().toInstant();
    }
}
