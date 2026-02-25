package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.StringReader;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class GitHubAppService {
    private final OrchestratorProperties properties;
    private final WebClient webClient;
    private final PrivateKey privateKey;

    public GitHubAppService(OrchestratorProperties properties, WebClient.Builder webClientBuilder,
            @org.springframework.beans.factory.annotation.Value("${atlasia.github.api-url:https://api.github.com}") String githubApiUrl) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(githubApiUrl).build();
        this.privateKey = loadPrivateKeySafely(properties.github().privateKeyPath());
    }

    private PrivateKey loadPrivateKeySafely(String path) {
        if (path == null || path.trim().isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(GitHubAppService.class)
                    .info("No GitHub App private key path provided - skipping GitHub App authentication.");
            return null;
        }
        try {
            return loadPrivateKey(path);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(GitHubAppService.class)
                    .error("Failed to load GitHub App private key from {}: {}", path, e.getMessage());
            return null;
        }
    }

    private PrivateKey loadPrivateKey(String path) throws IOException {
        java.nio.file.Path keyPath = java.nio.file.Paths.get(path);
        if (!java.nio.file.Files.isRegularFile(keyPath)) {
            throw new IOException("Not a regular file: " + path);
        }
        String content = java.nio.file.Files.readString(keyPath);
        try (PEMParser pemParser = new PEMParser(new StringReader(content))) {
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (object instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) object);
            }
            throw new IllegalArgumentException("Invalid private key format");
        }
    }

    private String generateJWT() {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(now.plusSeconds(600));

        return Jwts.builder()
                .setIssuedAt(issuedAt)
                .setExpiration(expiresAt)
                .setIssuer(properties.github().appId())
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public String getInstallationToken() {
        if (privateKey == null) {
            return null;
        }
        String jwt = generateJWT();
        String installationId = properties.github().installationId();
        if (installationId == null || installationId.trim().isEmpty()) {
            return null;
        }

        Map<String, Object> response = webClient.post()
                .uri("/app/installations/{installation_id}/access_tokens", installationId)
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return response != null ? (String) response.get("token") : null;
    }
}
