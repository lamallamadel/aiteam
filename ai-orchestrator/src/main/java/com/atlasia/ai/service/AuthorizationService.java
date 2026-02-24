package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class AuthorizationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationService.class);

    private final RoleService roleService;
    private final UserRepository userRepository;
    private final RunRepository runRepository;

    public AuthorizationService(RoleService roleService,
                               UserRepository userRepository,
                               RunRepository runRepository) {
        this.roleService = roleService;
        this.userRepository = userRepository;
        this.runRepository = runRepository;
    }

    public boolean hasPermission(UUID userId, String resource, String action) {
        return hasPermission(userId, resource, action, null);
    }

    public boolean hasPermission(UUID userId, String resource, String action, Object resourceObject) {
        logger.debug("Checking permission for userId={}, resource={}, action={}", userId, resource, action);

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            logger.warn("User not found: {}", userId);
            return false;
        }

        if (!user.isEnabled() || user.isLocked()) {
            logger.warn("User is disabled or locked: {}", userId);
            return false;
        }

        String permission = resource + ":" + action;
        Set<String> userPermissions = roleService.getUserPermissions(userId);

        if (userPermissions.contains(permission)) {
            if (resourceObject != null) {
                return checkAttributeBasedRules(user, resource, action, resourceObject);
            }
            return true;
        }

        logger.debug("User {} does not have permission {}", userId, permission);
        return false;
    }

    private boolean checkAttributeBasedRules(UserEntity user, String resource, String action, Object resourceObject) {
        if (RoleService.RESOURCE_RUN.equals(resource)) {
            return checkRunAccessRules(user, action, resourceObject);
        }
        
        return true;
    }

    private boolean checkRunAccessRules(UserEntity user, String action, Object resourceObject) {
        if (!(resourceObject instanceof RunEntity)) {
            if (resourceObject instanceof UUID) {
                RunEntity run = runRepository.findById((UUID) resourceObject).orElse(null);
                if (run == null) {
                    return false;
                }
                return checkRunOwnership(user, run, action);
            }
            return true;
        }

        RunEntity run = (RunEntity) resourceObject;
        return checkRunOwnership(user, run, action);
    }

    private boolean checkRunOwnership(UserEntity user, RunEntity run, String action) {
        Set<String> userRoles = roleService.getUserRoles(user.getId());
        
        if (userRoles.contains(RoleService.ROLE_ADMIN)) {
            return true;
        }

        if (userRoles.contains(RoleService.ROLE_WORKFLOW_MANAGER)) {
            return true;
        }

        return true;
    }

    public boolean hasRole(UUID userId, String roleName) {
        Set<String> userRoles = roleService.getUserRoles(userId);
        return userRoles.contains(roleName);
    }

    public boolean hasAnyRole(UUID userId, String... roleNames) {
        Set<String> userRoles = roleService.getUserRoles(userId);
        for (String roleName : roleNames) {
            if (userRoles.contains(roleName)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getUserPermissions(UUID userId) {
        return roleService.getUserPermissions(userId);
    }

    public Set<String> getUserRoles(UUID userId) {
        return roleService.getUserRoles(userId);
    }
}
