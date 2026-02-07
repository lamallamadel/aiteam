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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class GitHubAppService {
    private final OrchestratorProperties properties;
    private final WebClient webClient;
    private final PrivateKey privateKey;

    public GitHubAppService(OrchestratorProperties properties, WebClient.Builder webClientBuilder) throws IOException {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl("https://api.github.com").build();
        this.privateKey = loadPrivateKey(properties.github().privateKeyPath());
    }

    private PrivateKey loadPrivateKey(String path) throws IOException {
        String content = Files.readString(Paths.get(path));
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
        String jwt = generateJWT();
        String installationId = properties.github().installationId();

        Map<String, Object> response = webClient.post()
                .uri("/app/installations/{installation_id}/access_tokens", installationId)
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return (String) response.get("token");
    }
}
