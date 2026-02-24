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
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "file_path", length = 1000)
    private String filePath;

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
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getFilePath() { return filePath; }

    public void setRun(RunEntity run) { this.run = run; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
