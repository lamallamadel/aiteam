package com.atlasia.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_access_logs", indexes = {
    @Index(name = "idx_audit_access_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_access_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_access_resource", columnList = "resource_type,resource_id"),
    @Index(name = "idx_audit_access_event_hash", columnList = "event_hash")
})
public class AuditAccessLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "endpoint", length = 500)
    private String endpoint;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "retention_days")
    private Integer retentionDays = 2555;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "previous_event_hash", length = 64)
    private String previousEventHash;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;

    protected AuditAccessLogEntity() {}

    public AuditAccessLogEntity(UUID userId, String username, String resourceType, String resourceId,
                                String action, String httpMethod, String endpoint, String ipAddress,
                                String userAgent, Integer statusCode, Instant timestamp) {
        this.userId = userId;
        this.username = username;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.action = action;
        this.httpMethod = httpMethod;
        this.endpoint = endpoint;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.statusCode = statusCode;
        this.timestamp = timestamp;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getAction() { return action; }
    public String getHttpMethod() { return httpMethod; }
    public String getEndpoint() { return endpoint; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Integer getStatusCode() { return statusCode; }
    public Instant getTimestamp() { return timestamp; }
    public Integer getRetentionDays() { return retentionDays; }
    public Instant getArchivedAt() { return archivedAt; }
    public String getPreviousEventHash() { return previousEventHash; }
    public String getEventHash() { return eventHash; }

    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public void setPreviousEventHash(String previousEventHash) { this.previousEventHash = previousEventHash; }
    public void setEventHash(String eventHash) { this.eventHash = eventHash; }
}
