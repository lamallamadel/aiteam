package com.atlasia.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
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

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RunEntity() {}

    public RunEntity(UUID id, String repo, int issueNumber, String mode, String status, Instant createdAt) {
        this.id = id;
        this.repo = repo;
        this.issueNumber = issueNumber;
        this.mode = mode;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getRepo() { return repo; }
    public int getIssueNumber() { return issueNumber; }
    public String getMode() { return mode; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
}
