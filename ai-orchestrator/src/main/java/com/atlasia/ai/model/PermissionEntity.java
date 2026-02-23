package com.atlasia.ai.model;

import jakarta.persistence.*;

@Entity
@Table(name = "permissions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"resource", "action"})
})
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "resource", nullable = false, length = 100)
    private String resource;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    protected PermissionEntity() {}

    public PermissionEntity(String resource, String action) {
        this.resource = resource;
        this.action = action;
    }

    public PermissionEntity(String resource, String action, String description) {
        this.resource = resource;
        this.action = action;
        this.description = description;
    }

    public Integer getId() { return id; }
    public String getResource() { return resource; }
    public String getAction() { return action; }
    public String getDescription() { return description; }

    public void setResource(String resource) { this.resource = resource; }
    public void setAction(String action) { this.action = action; }
    public void setDescription(String description) { this.description = description; }

    public String getAuthority() {
        return resource + ":" + action;
    }
}
