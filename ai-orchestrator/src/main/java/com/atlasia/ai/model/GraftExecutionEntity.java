package com.atlasia.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "graft_executions")
public class GraftExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "graft_id", nullable = false)
    private String graftId;

    @Column(name = "agent_name", nullable = false)
    private String agentName;

    @Column(name = "checkpoint_after", nullable = false)
    private String checkpointAfter;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GraftExecutionStatus status;

    @Column(name = "output_artifact_id")
    private UUID outputArtifactId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "timeout_ms", nullable = false)
    private long timeoutMs = 300000L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GraftExecutionEntity() {}

    public GraftExecutionEntity(UUID runId, String graftId, String agentName, String checkpointAfter, long timeoutMs) {
        this.runId = runId;
        this.graftId = graftId;
        this.agentName = agentName;
        this.checkpointAfter = checkpointAfter;
        this.timeoutMs = timeoutMs;
        this.startedAt = Instant.now();
        this.status = GraftExecutionStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public String getGraftId() { return graftId; }
    public String getAgentName() { return agentName; }
    public String getCheckpointAfter() { return checkpointAfter; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public GraftExecutionStatus getStatus() { return status; }
    public UUID getOutputArtifactId() { return outputArtifactId; }
    public String getErrorMessage() { return errorMessage; }
    public int getRetryCount() { return retryCount; }
    public long getTimeoutMs() { return timeoutMs; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(GraftExecutionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
        this.updatedAt = Instant.now();
    }

    public void setOutputArtifactId(UUID outputArtifactId) {
        this.outputArtifactId = outputArtifactId;
        this.updatedAt = Instant.now();
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
        this.updatedAt = Instant.now();
    }

    public enum GraftExecutionStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        TIMEOUT,
        CIRCUIT_OPEN
    }
}
