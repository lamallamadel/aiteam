package com.atlasia.ai.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists a persona-to-persona handoff in a Chat Mode session.
 *
 * Lifecycle: PENDING → ACCEPTED → COMPLETED (or REJECTED).
 * Once ACCEPTED, the receiving persona's next system prompt is prefixed
 * with a briefing block built from this record.
 */
@Entity
@Table(name = "persona_handoff")
public class PersonaHandoffEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "from_persona_id", nullable = false, length = 100)
    private String fromPersonaId;

    @Column(name = "from_session_key", nullable = false)
    private String fromSessionKey;

    @Column(name = "to_persona_id", nullable = false, length = 100)
    private String toPersonaId;

    @Column(name = "to_session_key")
    private String toSessionKey;

    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    @Column(name = "trigger_reason", nullable = false, columnDefinition = "TEXT")
    private String triggerReason;

    @Column(name = "handoff_summary", nullable = false, columnDefinition = "TEXT")
    private String handoffSummary;

    /** JSON-serialised map of key facts to pass to the receiving persona */
    @Column(name = "shared_context", columnDefinition = "TEXT")
    private String sharedContext;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PersonaHandoffEntity() {}

    public PersonaHandoffEntity(
            String userId,
            String fromPersonaId,
            String fromSessionKey,
            String toPersonaId,
            String triggerType,
            String triggerReason,
            String handoffSummary) {
        this.userId = userId;
        this.fromPersonaId = fromPersonaId;
        this.fromSessionKey = fromSessionKey;
        this.toPersonaId = toPersonaId;
        this.triggerType = triggerType;
        this.triggerReason = triggerReason;
        this.handoffSummary = handoffSummary;
        this.status = "PENDING";
    }

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public String getFromPersonaId() { return fromPersonaId; }
    public String getFromSessionKey() { return fromSessionKey; }
    public String getToPersonaId() { return toPersonaId; }
    public String getToSessionKey() { return toSessionKey; }
    public void setToSessionKey(String toSessionKey) { this.toSessionKey = toSessionKey; }
    public String getTriggerType() { return triggerType; }
    public String getTriggerReason() { return triggerReason; }
    public String getHandoffSummary() { return handoffSummary; }
    public String getSharedContext() { return sharedContext; }
    public void setSharedContext(String sharedContext) { this.sharedContext = sharedContext; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
