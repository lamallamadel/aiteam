package com.atlasia.ai.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_run_artifact")
public class RunArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private RunEntity run;

    @Column(name = "agent_name", nullable = false)
    private String agentName;

    @Column(name = "artifact_type", nullable = false)
    private String artifactType;

    @Type(JsonBinaryType.class)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RunArtifactEntity() {}

    public RunArtifactEntity(String agentName, String artifactType, String payload, Instant createdAt) {
        this.agentName = agentName;
        this.artifactType = artifactType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public RunEntity getRun() { return run; }
    public String getAgentName() { return agentName; }
    public String getArtifactType() { return artifactType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }

    public void setRun(RunEntity run) { this.run = run; }
}
