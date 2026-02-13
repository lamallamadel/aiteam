package com.atlasia.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_trace_event")
public class TraceEventEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "parent_event_id")
    private UUID parentEventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "agent_name", length = 100)
    private String agentName;

    @Column(nullable = false)
    private String label;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    protected TraceEventEntity() {}

    public TraceEventEntity(UUID id, UUID runId, UUID parentEventId, String eventType,
                            String agentName, String label, Instant startTime) {
        this.id = id;
        this.runId = runId;
        this.parentEventId = parentEventId;
        this.eventType = eventType;
        this.agentName = agentName;
        this.label = label;
        this.startTime = startTime;
        this.metadata = "{}";
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public UUID getParentEventId() { return parentEventId; }
    public String getEventType() { return eventType; }
    public String getAgentName() { return agentName; }
    public String getLabel() { return label; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Long getDurationMs() { return durationMs; }
    public Integer getTokensUsed() { return tokensUsed; }
    public String getMetadata() { return metadata; }

    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public void setTokensUsed(Integer tokensUsed) { this.tokensUsed = tokensUsed; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
