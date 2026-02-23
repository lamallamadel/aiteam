package com.atlasia.ai.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "persisted_collaboration_messages", indexes = {
    @Index(name = "idx_run_id_timestamp", columnList = "run_id,timestamp DESC")
})
public class PersistedCollaborationMessage {

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
    @Column(name = "message_data", columnDefinition = "jsonb")
    private String messageData;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "is_critical", nullable = false)
    private Boolean isCritical;

    protected PersistedCollaborationMessage() {}

    public PersistedCollaborationMessage(UUID runId, String userId, String eventType, 
                                         String messageData, Instant timestamp, 
                                         Long sequenceNumber, Boolean isCritical) {
        this.runId = runId;
        this.userId = userId;
        this.eventType = eventType;
        this.messageData = messageData;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
        this.isCritical = isCritical;
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public String getUserId() { return userId; }
    public String getEventType() { return eventType; }
    public String getMessageData() { return messageData; }
    public Instant getTimestamp() { return timestamp; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public Boolean getIsCritical() { return isCritical; }
}
