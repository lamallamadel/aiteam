package com.atlasia.ai.model;

import com.atlasia.ai.config.Encrypted;
import com.atlasia.ai.config.EncryptedStringConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Encrypted(reason = "MFA secrets allow account access bypass")
    @Column(name = "mfa_secret_encrypted", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String mfaSecret;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_permissions",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<PermissionEntity> permissions = new HashSet<>();

    protected UserEntity() {}

    public UserEntity(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getMfaSecret() { return mfaSecret; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isEnabled() { return enabled; }
    public boolean isLocked() { return locked; }
    public Set<RoleEntity> getRoles() { return roles; }
    public Set<PermissionEntity> getPermissions() { return permissions; }

    public void setUsername(String username) {
        this.username = username;
        this.updatedAt = Instant.now();
    }

    public void setEmail(String email) {
        this.email = email;
        this.updatedAt = Instant.now();
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public void setMfaSecret(String mfaSecret) {
        this.mfaSecret = mfaSecret;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        this.updatedAt = Instant.now();
    }

    public void setRoles(Set<RoleEntity> roles) { 
        this.roles = roles;
        this.updatedAt = Instant.now();
    }

    public void setPermissions(Set<PermissionEntity> permissions) { 
        this.permissions = permissions;
        this.updatedAt = Instant.now();
    }

    public void addRole(RoleEntity role) {
        this.roles.add(role);
        this.updatedAt = Instant.now();
    }

    public void removeRole(RoleEntity role) {
        this.roles.remove(role);
        this.updatedAt = Instant.now();
    }

    public void addPermission(PermissionEntity permission) {
        this.permissions.add(permission);
        this.updatedAt = Instant.now();
    }

    public void removePermission(PermissionEntity permission) {
        this.permissions.remove(permission);
        this.updatedAt = Instant.now();
    }

    public boolean isMfaEnabled() {
        return mfaSecret != null && !mfaSecret.isEmpty();
    }
}
