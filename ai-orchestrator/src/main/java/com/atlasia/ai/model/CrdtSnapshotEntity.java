package com.atlasia.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crdt_snapshots", indexes = {
    @Index(name = "idx_crdt_snapshot_run", columnList = "run_id,created_at DESC")
})
public class CrdtSnapshotEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    
    @Column(name = "run_id", nullable = false)
    private UUID runId;
    
    @Column(name = "snapshot_data", columnDefinition = "bytea", nullable = false)
    private byte[] snapshotData;
    
    @Column(name = "lamport_timestamp", nullable = false)
    private Long lamportTimestamp;
    
    @Column(name = "region", length = 50, nullable = false)
    private String region;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "event_count")
    private Integer eventCount;
    
    protected CrdtSnapshotEntity() {}
    
    public CrdtSnapshotEntity(UUID runId, byte[] snapshotData, Long lamportTimestamp, 
                             String region, Instant createdAt, Integer eventCount) {
        this.runId = runId;
        this.snapshotData = snapshotData;
        this.lamportTimestamp = lamportTimestamp;
        this.region = region;
        this.createdAt = createdAt;
        this.eventCount = eventCount;
    }
    
    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public byte[] getSnapshotData() { return snapshotData; }
    public Long getLamportTimestamp() { return lamportTimestamp; }
    public String getRegion() { return region; }
    public Instant getCreatedAt() { return createdAt; }
    public Integer getEventCount() { return eventCount; }
}
