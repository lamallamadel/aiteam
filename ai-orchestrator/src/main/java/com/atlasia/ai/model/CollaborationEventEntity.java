package com.atlasia.ai.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collaboration_events", indexes = {
    @Index(name = "idx_run_timestamp", columnList = "run_id,timestamp"),
    @Index(name = "idx_run_user", columnList = "run_id,user_id"),
    @Index(name = "idx_run_event_type", columnList = "run_id,event_type")
})
public class CollaborationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Type(JsonBinaryType.class)
    @Column(name = "event_data", columnDefinition = "jsonb")
    private String eventData;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    @Column(name = "state_before", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private String stateBefore;
    
    @Column(name = "state_after", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private String stateAfter;
    
    @Column(name = "retention_days")
    private Integer retentionDays = 2555;
    
    @Column(name = "archived_at")
    private Instant archivedAt;
    
    @Column(name = "previous_event_hash", length = 64)
    private String previousEventHash;
    
    @Column(name = "event_hash", length = 64)
    private String eventHash;
    
    @Column(name = "crdt_changes", columnDefinition = "bytea")
    private byte[] crdtChanges;
    
    @Column(name = "source_region", length = 50)
    private String sourceRegion;
    
    @Column(name = "lamport_timestamp")
    private Long lamportTimestamp;

    protected CollaborationEventEntity() {}

    public CollaborationEventEntity(UUID runId, String userId, String eventType, String eventData, Instant timestamp) {
        this.runId = runId;
        this.userId = userId;
        this.eventType = eventType;
        this.eventData = eventData;
        this.timestamp = timestamp;
    }
    
    public CollaborationEventEntity(UUID runId, String userId, String eventType, String eventData, 
                                   Instant timestamp, byte[] crdtChanges, String sourceRegion, Long lamportTimestamp) {
        this.runId = runId;
        this.userId = userId;
        this.eventType = eventType;
        this.eventData = eventData;
        this.timestamp = timestamp;
        this.crdtChanges = crdtChanges;
        this.sourceRegion = sourceRegion;
        this.lamportTimestamp = lamportTimestamp;
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public String getUserId() { return userId; }
    public String getEventType() { return eventType; }
    public String getEventData() { return eventData; }
    public Instant getTimestamp() { return timestamp; }
    public String getStateBefore() { return stateBefore; }
    public String getStateAfter() { return stateAfter; }
    public Integer getRetentionDays() { return retentionDays; }
    public Instant getArchivedAt() { return archivedAt; }
    public String getPreviousEventHash() { return previousEventHash; }
    public String getEventHash() { return eventHash; }
    public byte[] getCrdtChanges() { return crdtChanges; }
    public String getSourceRegion() { return sourceRegion; }
    public Long getLamportTimestamp() { return lamportTimestamp; }
    
    public void setStateBefore(String stateBefore) { this.stateBefore = stateBefore; }
    public void setStateAfter(String stateAfter) { this.stateAfter = stateAfter; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public void setPreviousEventHash(String previousEventHash) { this.previousEventHash = previousEventHash; }
    public void setEventHash(String eventHash) { this.eventHash = eventHash; }
    public void setCrdtChanges(byte[] crdtChanges) { this.crdtChanges = crdtChanges; }
    public void setSourceRegion(String sourceRegion) { this.sourceRegion = sourceRegion; }
    public void setLamportTimestamp(Long lamportTimestamp) { this.lamportTimestamp = lamportTimestamp; }
}
