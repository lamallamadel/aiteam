package com.atlasia.ai.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_run")
public class RunEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repo", nullable = false)
    private String repo;

    @Column(name = "issue_number", nullable = false)
    private int issueNumber;

    @Column(name = "mode", nullable = false)
    private String mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RunStatus status;

    @Column(name = "current_agent")
    private String currentAgent;

    @Column(name = "ci_fix_count", nullable = false)
    private int ciFixCount = 0;

    @Column(name = "e2e_fix_count", nullable = false)
    private int e2eFixCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "autonomy")
    private String autonomy = "autonomous";

    @Column(name = "autonomy_dev_gate_passed")
    private boolean autonomyDevGatePassed = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment_lifecycle")
    private EnvironmentLifecycle environmentLifecycle = EnvironmentLifecycle.ACTIVE;

    @Type(JsonBinaryType.class)
    @Column(name = "environment_checkpoint", columnDefinition = "jsonb")
    private String environmentCheckpoint;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RunArtifactEntity> artifacts = new ArrayList<>();

    protected RunEntity() {}

    public RunEntity(UUID id, String repo, int issueNumber, String mode, RunStatus status, Instant createdAt) {
        this.id = id;
        this.repo = repo;
        this.issueNumber = issueNumber;
        this.mode = mode;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getRepo() { return repo; }
    public int getIssueNumber() { return issueNumber; }
    public String getMode() { return mode; }
    public RunStatus getStatus() { return status; }
    public String getCurrentAgent() { return currentAgent; }
    public int getCiFixCount() { return ciFixCount; }
    public int getE2eFixCount() { return e2eFixCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<RunArtifactEntity> getArtifacts() { return artifacts; }
    public String getAutonomy() { return autonomy != null ? autonomy : "autonomous"; }
    public boolean isAutonomyDevGatePassed() { return autonomyDevGatePassed; }
    public EnvironmentLifecycle getEnvironmentLifecycle() { return environmentLifecycle; }
    public String getEnvironmentCheckpoint() { return environmentCheckpoint; }

    public void setAutonomy(String autonomy) { this.autonomy = autonomy; }
    public void setAutonomyDevGatePassed(boolean passed) {
        this.autonomyDevGatePassed = passed;
        this.updatedAt = Instant.now();
    }

    public void setEnvironmentLifecycle(EnvironmentLifecycle environmentLifecycle) {
        this.environmentLifecycle = environmentLifecycle;
        this.updatedAt = Instant.now();
    }

    public void setEnvironmentCheckpoint(String environmentCheckpoint) {
        this.environmentCheckpoint = environmentCheckpoint;
        this.updatedAt = Instant.now();
    }

    public void setStatus(RunStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setCurrentAgent(String currentAgent) { 
        this.currentAgent = currentAgent;
        this.updatedAt = Instant.now();
    }

    public void incrementCiFixCount() {
        this.ciFixCount++;
        this.updatedAt = Instant.now();
    }

    public void incrementE2eFixCount() {
        this.e2eFixCount++;
        this.updatedAt = Instant.now();
    }

    public void addArtifact(RunArtifactEntity artifact) {
        artifacts.add(artifact);
        artifact.setRun(this);
    }
}
