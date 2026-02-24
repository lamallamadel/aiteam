package com.atlasia.ai.model;

import com.atlasia.ai.config.Encrypted;
import com.atlasia.ai.config.EncryptedStringConverter;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "oauth2_accounts")
public class OAuth2AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Encrypted(reason = "OAuth2 access tokens contain sensitive credentials")
    @Column(name = "access_token_encrypted", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String accessToken;

    @Encrypted(reason = "OAuth2 refresh tokens allow long-term access")
    @Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String refreshToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OAuth2AccountEntity() {}

    public OAuth2AccountEntity(UserEntity user, String provider, String providerUserId, String accessToken, String refreshToken) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public UserEntity getUser() { return user; }
    public String getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public Instant getCreatedAt() { return createdAt; }

    public void setUser(UserEntity user) { this.user = user; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setProviderUserId(String providerUserId) { this.providerUserId = providerUserId; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
