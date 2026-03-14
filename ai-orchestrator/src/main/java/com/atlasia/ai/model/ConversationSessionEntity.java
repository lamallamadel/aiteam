package com.atlasia.ai.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversation_session")
public class ConversationSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Composite key: userId::personaId — unique per user+persona pair */
    @Column(name = "session_key", nullable = false, unique = true)
    private String sessionKey;

    @Column(name = "persona_id", nullable = false)
    private String personaId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_active", nullable = false)
    private Instant lastActive;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ConversationTurnEntity> turns = new ArrayList<>();

    protected ConversationSessionEntity() {}

    public ConversationSessionEntity(String sessionKey, String userId, String personaId) {
        this.sessionKey = sessionKey;
        this.userId = userId;
        this.personaId = personaId;
    }

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        lastActive = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSessionKey() { return sessionKey; }
    public String getUserId() { return userId; }
    public String getPersonaId() { return personaId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActive() { return lastActive; }
    public List<ConversationTurnEntity> getTurns() { return turns; }

    public void setLastActive(Instant lastActive) { this.lastActive = lastActive; }
}
