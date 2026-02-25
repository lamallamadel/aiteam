package com.atlasia.ai.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_configs")
public class NotificationConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "webhook_url", nullable = false, columnDefinition = "TEXT")
    private String webhookUrl;

    @Type(JsonBinaryType.class)
    @Column(name = "enabled_events", nullable = false, columnDefinition = "jsonb")
    private String enabledEvents;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationConfigEntity() {}

    public NotificationConfigEntity(UUID userId, String provider, String webhookUrl, String enabledEvents) {
        this.userId = userId;
        this.provider = provider;
        this.webhookUrl = webhookUrl;
        this.enabledEvents = enabledEvents;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getWebhookUrl() { return webhookUrl; }
    public String getEnabledEvents() { return enabledEvents; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.updatedAt = Instant.now();
    }

    public void setEnabledEvents(String enabledEvents) {
        this.enabledEvents = enabledEvents;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
