package com.atlasia.ai.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<PermissionEntity> permissions = new HashSet<>();

    protected RoleEntity() {}

    public RoleEntity(String name) {
        this.name = name;
    }

    public RoleEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Set<PermissionEntity> getPermissions() { return permissions; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPermissions(Set<PermissionEntity> permissions) { this.permissions = permissions; }

    public void addPermission(PermissionEntity permission) {
        this.permissions.add(permission);
    }

    public void removePermission(PermissionEntity permission) {
        this.permissions.remove(permission);
    }
}
