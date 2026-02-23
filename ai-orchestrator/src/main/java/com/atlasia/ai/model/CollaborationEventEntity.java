package com.atlasia.ai.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collaboration_events")
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

    protected CollaborationEventEntity() {}

    public CollaborationEventEntity(UUID runId, String userId, String eventType, String eventData, Instant timestamp) {
        this.runId = runId;
        this.userId = userId;
        this.eventType = eventType;
        this.eventData = eventData;
        this.timestamp = timestamp;
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public String getUserId() { return userId; }
    public String getEventType() { return eventType; }
    public String getEventData() { return eventData; }
    public Instant getTimestamp() { return timestamp; }
}
