package com.atlasia.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_admin_actions", indexes = {
    @Index(name = "idx_audit_admin_admin_user_id", columnList = "admin_user_id"),
    @Index(name = "idx_audit_admin_target_user_id", columnList = "target_user_id"),
    @Index(name = "idx_audit_admin_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_admin_action_type", columnList = "action_type"),
    @Index(name = "idx_audit_admin_event_hash", columnList = "event_hash")
})
public class AuditAdminActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "admin_user_id")
    private UUID adminUserId;

    @Column(name = "admin_username", nullable = false)
    private String adminUsername;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "target_username")
    private String targetUsername;

    @Column(name = "action_details", columnDefinition = "TEXT")
    private String actionDetails;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

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

    protected AuditAdminActionEntity() {}

    public AuditAdminActionEntity(UUID adminUserId, String adminUsername, String actionType,
                                  UUID targetUserId, String targetUsername, String actionDetails,
                                  String ipAddress, Instant timestamp) {
        this.adminUserId = adminUserId;
        this.adminUsername = adminUsername;
        this.actionType = actionType;
        this.targetUserId = targetUserId;
        this.targetUsername = targetUsername;
        this.actionDetails = actionDetails;
        this.ipAddress = ipAddress;
        this.timestamp = timestamp;
    }

    public UUID getId() { return id; }
    public UUID getAdminUserId() { return adminUserId; }
    public String getAdminUsername() { return adminUsername; }
    public String getActionType() { return actionType; }
    public UUID getTargetUserId() { return targetUserId; }
    public String getTargetUsername() { return targetUsername; }
    public String getActionDetails() { return actionDetails; }
    public String getIpAddress() { return ipAddress; }
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
