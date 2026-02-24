package com.atlasia.ai.service;

import com.atlasia.ai.model.PermissionEntity;
import com.atlasia.ai.model.RoleEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.PermissionRepository;
import com.atlasia.ai.persistence.RoleRepository;
import com.atlasia.ai.persistence.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RoleService {

    private static final Logger logger = LoggerFactory.getLogger(RoleService.class);

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_WORKFLOW_MANAGER = "WORKFLOW_MANAGER";
    public static final String ROLE_VIEWER = "VIEWER";

    public static final String RESOURCE_RUN = "run";
    public static final String RESOURCE_SETTINGS = "settings";
    public static final String RESOURCE_USERS = "users";
    public static final String RESOURCE_GRAFT = "graft";
    public static final String RESOURCE_OVERSIGHT = "oversight";
    public static final String RESOURCE_ANALYTICS = "analytics";

    public static final String ACTION_CREATE = "create";
    public static final String ACTION_VIEW = "view";
    public static final String ACTION_UPDATE = "update";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_MANAGE = "manage";

    public static final String PERMISSION_RUN_CREATE = RESOURCE_RUN + ":" + ACTION_CREATE;
    public static final String PERMISSION_RUN_VIEW = RESOURCE_RUN + ":" + ACTION_VIEW;
    public static final String PERMISSION_RUN_UPDATE = RESOURCE_RUN + ":" + ACTION_UPDATE;
    public static final String PERMISSION_RUN_DELETE = RESOURCE_RUN + ":" + ACTION_DELETE;
    public static final String PERMISSION_SETTINGS_MANAGE = RESOURCE_SETTINGS + ":" + ACTION_MANAGE;
    public static final String PERMISSION_USERS_MANAGE = RESOURCE_USERS + ":" + ACTION_MANAGE;
    public static final String PERMISSION_GRAFT_CREATE = RESOURCE_GRAFT + ":" + ACTION_CREATE;
    public static final String PERMISSION_GRAFT_VIEW = RESOURCE_GRAFT + ":" + ACTION_VIEW;
    public static final String PERMISSION_GRAFT_MANAGE = RESOURCE_GRAFT + ":" + ACTION_MANAGE;
    public static final String PERMISSION_OVERSIGHT_VIEW = RESOURCE_OVERSIGHT + ":" + ACTION_VIEW;
    public static final String PERMISSION_OVERSIGHT_MANAGE = RESOURCE_OVERSIGHT + ":" + ACTION_MANAGE;
    public static final String PERMISSION_ANALYTICS_VIEW = RESOURCE_ANALYTICS + ":" + ACTION_VIEW;

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public RoleService(RoleRepository roleRepository, 
                      PermissionRepository permissionRepository,
                      UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    @Transactional
    public void initializeRolesAndPermissions() {
        try {
            logger.info("Initializing roles and permissions...");

            Map<String, PermissionEntity> permissions = createPermissions();
            createRoles(permissions);

            logger.info("Roles and permissions initialized successfully");
        } catch (Exception e) {
            logger.warn("Could not initialize roles and permissions (may already exist or DB not ready): {}", e.getMessage());
        }
    }

    private Map<String, PermissionEntity> createPermissions() {
        Map<String, PermissionEntity> permissions = new HashMap<>();

        List<PermissionSpec> specs = Arrays.asList(
            new PermissionSpec(RESOURCE_RUN, ACTION_CREATE, "Create workflow runs"),
            new PermissionSpec(RESOURCE_RUN, ACTION_VIEW, "View workflow runs"),
            new PermissionSpec(RESOURCE_RUN, ACTION_UPDATE, "Update workflow runs"),
            new PermissionSpec(RESOURCE_RUN, ACTION_DELETE, "Delete workflow runs"),
            new PermissionSpec(RESOURCE_SETTINGS, ACTION_MANAGE, "Manage system settings"),
            new PermissionSpec(RESOURCE_USERS, ACTION_MANAGE, "Manage users"),
            new PermissionSpec(RESOURCE_GRAFT, ACTION_CREATE, "Create grafts"),
            new PermissionSpec(RESOURCE_GRAFT, ACTION_VIEW, "View grafts"),
            new PermissionSpec(RESOURCE_GRAFT, ACTION_MANAGE, "Manage grafts"),
            new PermissionSpec(RESOURCE_OVERSIGHT, ACTION_VIEW, "View oversight configuration"),
            new PermissionSpec(RESOURCE_OVERSIGHT, ACTION_MANAGE, "Manage oversight configuration"),
            new PermissionSpec(RESOURCE_ANALYTICS, ACTION_VIEW, "View analytics")
        );

        for (PermissionSpec spec : specs) {
            PermissionEntity permission = permissionRepository
                .findByResourceAndAction(spec.resource, spec.action)
                .orElseGet(() -> {
                    PermissionEntity p = new PermissionEntity(spec.resource, spec.action, spec.description);
                    return permissionRepository.save(p);
                });
            permissions.put(permission.getAuthority(), permission);
        }

        return permissions;
    }

    private void createRoles(Map<String, PermissionEntity> permissions) {
        createAdminRole(permissions);
        createUserRole(permissions);
        createWorkflowManagerRole(permissions);
        createViewerRole(permissions);
    }

    private void createAdminRole(Map<String, PermissionEntity> permissions) {
        RoleEntity admin = roleRepository.findByName(ROLE_ADMIN)
            .orElseGet(() -> {
                RoleEntity role = new RoleEntity(ROLE_ADMIN, "System administrator with full access");
                return roleRepository.save(role);
            });

        admin.getPermissions().clear();
        permissions.values().forEach(admin::addPermission);
        roleRepository.save(admin);
    }

    private void createUserRole(Map<String, PermissionEntity> permissions) {
        RoleEntity user = roleRepository.findByName(ROLE_USER)
            .orElseGet(() -> {
                RoleEntity role = new RoleEntity(ROLE_USER, "Regular user with standard access");
                return roleRepository.save(role);
            });

        user.getPermissions().clear();
        user.addPermission(permissions.get(PERMISSION_RUN_CREATE));
        user.addPermission(permissions.get(PERMISSION_RUN_VIEW));
        user.addPermission(permissions.get(PERMISSION_RUN_UPDATE));
        user.addPermission(permissions.get(PERMISSION_GRAFT_VIEW));
        user.addPermission(permissions.get(PERMISSION_OVERSIGHT_VIEW));
        user.addPermission(permissions.get(PERMISSION_ANALYTICS_VIEW));
        roleRepository.save(user);
    }

    private void createWorkflowManagerRole(Map<String, PermissionEntity> permissions) {
        RoleEntity manager = roleRepository.findByName(ROLE_WORKFLOW_MANAGER)
            .orElseGet(() -> {
                RoleEntity role = new RoleEntity(ROLE_WORKFLOW_MANAGER, "Workflow manager with extended permissions");
                return roleRepository.save(role);
            });

        manager.getPermissions().clear();
        manager.addPermission(permissions.get(PERMISSION_RUN_CREATE));
        manager.addPermission(permissions.get(PERMISSION_RUN_VIEW));
        manager.addPermission(permissions.get(PERMISSION_RUN_UPDATE));
        manager.addPermission(permissions.get(PERMISSION_RUN_DELETE));
        manager.addPermission(permissions.get(PERMISSION_GRAFT_CREATE));
        manager.addPermission(permissions.get(PERMISSION_GRAFT_VIEW));
        manager.addPermission(permissions.get(PERMISSION_GRAFT_MANAGE));
        manager.addPermission(permissions.get(PERMISSION_OVERSIGHT_VIEW));
        manager.addPermission(permissions.get(PERMISSION_OVERSIGHT_MANAGE));
        manager.addPermission(permissions.get(PERMISSION_ANALYTICS_VIEW));
        roleRepository.save(manager);
    }

    private void createViewerRole(Map<String, PermissionEntity> permissions) {
        RoleEntity viewer = roleRepository.findByName(ROLE_VIEWER)
            .orElseGet(() -> {
                RoleEntity role = new RoleEntity(ROLE_VIEWER, "Read-only access to system resources");
                return roleRepository.save(role);
            });

        viewer.getPermissions().clear();
        viewer.addPermission(permissions.get(PERMISSION_RUN_VIEW));
        viewer.addPermission(permissions.get(PERMISSION_GRAFT_VIEW));
        viewer.addPermission(permissions.get(PERMISSION_OVERSIGHT_VIEW));
        viewer.addPermission(permissions.get(PERMISSION_ANALYTICS_VIEW));
        roleRepository.save(viewer);
    }

    @Transactional
    public void assignRoleToUser(UUID userId, String roleName) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        RoleEntity role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        
        user.addRole(role);
        userRepository.save(user);
    }

    @Transactional
    public void removeRoleFromUser(UUID userId, String roleName) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        RoleEntity role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        
        user.removeRole(role);
        userRepository.save(user);
    }

    public Set<String> getUserRoles(UUID userId) {
        return userRepository.findById(userId)
            .map(user -> {
                Set<String> roleNames = new HashSet<>();
                user.getRoles().forEach(role -> roleNames.add(role.getName()));
                return roleNames;
            })
            .orElse(Collections.emptySet());
    }

    public Set<String> getUserPermissions(UUID userId) {
        return userRepository.findByIdWithRoles(userId)
            .map(user -> {
                Set<String> permissions = new HashSet<>();
                
                user.getRoles().forEach(role -> 
                    role.getPermissions().forEach(p -> permissions.add(p.getAuthority()))
                );
                
                user.getPermissions().forEach(p -> permissions.add(p.getAuthority()));
                
                return permissions;
            })
            .orElse(Collections.emptySet());
    }

    public List<RoleEntity> getAllRoles() {
        return roleRepository.findAll();
    }

    public Optional<RoleEntity> getRoleByName(String name) {
        return roleRepository.findByName(name);
    }

    private static class PermissionSpec {
        String resource;
        String action;
        String description;

        PermissionSpec(String resource, String action, String description) {
            this.resource = resource;
            this.action = action;
            this.description = description;
        }
    }
}
