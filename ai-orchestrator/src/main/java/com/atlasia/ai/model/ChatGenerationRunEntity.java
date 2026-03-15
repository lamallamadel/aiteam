package com.atlasia.ai.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a single Chat Mode code generation turn — one user prompt → N generated files.
 */
@Entity
@Table(name = "chat_generation_run")
public class ChatGenerationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_key", nullable = false)
    private String sessionKey;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "persona_id", nullable = false, length = 100)
    private String personaId;

    @Column(name = "user_prompt", nullable = false, columnDefinition = "TEXT")
    private String userPrompt;

    /** COMPLETED | FAILED | PARTIAL */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "artifact_count", nullable = false)
    private int artifactCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatGenerationRunEntity() {}

    public ChatGenerationRunEntity(String sessionKey, String userId, String personaId,
                                   String userPrompt, String status, int artifactCount) {
        this.sessionKey = sessionKey;
        this.userId = userId;
        this.personaId = personaId;
        this.userPrompt = userPrompt;
        this.status = status;
        this.artifactCount = artifactCount;
    }

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSessionKey() { return sessionKey; }
    public String getUserId() { return userId; }
    public String getPersonaId() { return personaId; }
    public String getUserPrompt() { return userPrompt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getArtifactCount() { return artifactCount; }
    public void setArtifactCount(int artifactCount) { this.artifactCount = artifactCount; }
    public Instant getCreatedAt() { return createdAt; }
}
