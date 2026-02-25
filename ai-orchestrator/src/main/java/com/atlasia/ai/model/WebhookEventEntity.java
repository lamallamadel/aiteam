package com.atlasia.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events", indexes = {
    @Index(name = "idx_webhook_events_event_type", columnList = "event_type"),
    @Index(name = "idx_webhook_events_processed_at", columnList = "processed_at"),
    @Index(name = "idx_webhook_events_signature_valid", columnList = "signature_valid")
})
public class WebhookEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false, length = 100000)
    private String payload;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected WebhookEventEntity() {}

    public WebhookEventEntity(String eventType, String payload, boolean signatureValid, Instant processedAt) {
        this.eventType = eventType;
        this.payload = payload;
        this.signatureValid = signatureValid;
        this.processedAt = processedAt;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public boolean isSignatureValid() { return signatureValid; }
    public Instant getProcessedAt() { return processedAt; }
}
