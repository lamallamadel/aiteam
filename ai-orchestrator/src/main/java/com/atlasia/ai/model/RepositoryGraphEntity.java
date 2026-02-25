package com.atlasia.ai.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repository_graph")
public class RepositoryGraphEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repo_url", nullable = false, unique = true, length = 500)
    private String repoUrl;

    @Type(JsonBinaryType.class)
    @Column(name = "dependencies", columnDefinition = "jsonb", nullable = false)
    private String dependencies;

    @Column(name = "workspace_type", length = 50)
    private String workspaceType;

    @Type(JsonBinaryType.class)
    @Column(name = "workspace_config", columnDefinition = "jsonb")
    private String workspaceConfig;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RepositoryGraphEntity() {}

    public RepositoryGraphEntity(String repoUrl, String dependencies) {
        this.repoUrl = repoUrl;
        this.dependencies = dependencies != null ? dependencies : "[]";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getRepoUrl() { return repoUrl; }
    public String getDependencies() { return dependencies; }
    public String getWorkspaceType() { return workspaceType; }
    public String getWorkspaceConfig() { return workspaceConfig; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
        this.updatedAt = Instant.now();
    }

    public void setDependencies(String dependencies) {
        this.dependencies = dependencies;
        this.updatedAt = Instant.now();
    }

    public void setWorkspaceType(String workspaceType) {
        this.workspaceType = workspaceType;
        this.updatedAt = Instant.now();
    }

    public void setWorkspaceConfig(String workspaceConfig) {
        this.workspaceConfig = workspaceConfig;
        this.updatedAt = Instant.now();
    }
}
