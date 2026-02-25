package com.atlasia.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_data_mutations", indexes = {
    @Index(name = "idx_audit_mutation_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_mutation_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_mutation_entity", columnList = "entity_type,entity_id"),
    @Index(name = "idx_audit_mutation_event_hash", columnList = "event_hash")
})
public class AuditDataMutationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "operation", nullable = false, length = 20)
    private String operation;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

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

    protected AuditDataMutationEntity() {}

    public AuditDataMutationEntity(UUID userId, String username, String entityType, String entityId,
                                   String operation, String fieldName, String oldValue, String newValue,
                                   Instant timestamp) {
        this.userId = userId;
        this.username = username;
        this.entityType = entityType;
        this.entityId = entityId;
        this.operation = operation;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = timestamp;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getOperation() { return operation; }
    public String getFieldName() { return fieldName; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
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
