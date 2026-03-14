package com.atlasia.ai.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_turn")
public class ConversationTurnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ConversationSessionEntity session;

    /** "user" | "assistant" */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConversationTurnEntity() {}

    public ConversationTurnEntity(ConversationSessionEntity session, String role, String content) {
        this.session = session;
        this.role = role;
        this.content = content;
    }

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ConversationSessionEntity getSession() { return session; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
