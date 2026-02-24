package com.atlasia.ai.config;

import com.atlasia.ai.service.AuthorizationService;
import com.atlasia.ai.service.JwtService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

@Aspect
@Component
@Order(1)
public class PermissionEvaluatorAspect {

    private static final Logger logger = LoggerFactory.getLogger(PermissionEvaluatorAspect.class);

    private final AuthorizationService authorizationService;
    private final JwtService jwtService;

    public PermissionEvaluatorAspect(AuthorizationService authorizationService, JwtService jwtService) {
        this.authorizationService = authorizationService;
        this.jwtService = jwtService;
    }

    @Around("@annotation(com.atlasia.ai.config.RequiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);

        if (annotation == null) {
            return joinPoint.proceed();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            logger.warn("Unauthenticated access attempt to {}.{}", 
                joinPoint.getTarget().getClass().getSimpleName(), 
                method.getName());
            throw new AccessDeniedException("Authentication required");
        }

        UUID userId = extractUserId(authentication);
        String resource = annotation.resource();
        String action = annotation.action();

        Object resourceObject = findResourceObject(joinPoint);

        boolean hasPermission = authorizationService.hasPermission(userId, resource, action, resourceObject);

        if (!hasPermission) {
            logger.warn("Access denied for user {} to {}:{} on {}.{}", 
                userId, resource, action,
                joinPoint.getTarget().getClass().getSimpleName(),
                method.getName());
            throw new AccessDeniedException(
                String.format("User does not have permission to %s %s", action, resource));
        }

        logger.debug("Access granted for user {} to {}:{}", userId, resource, action);
        return joinPoint.proceed();
    }

    private UUID extractUserId(Authentication authentication) {
        try {
            if (authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
                org.springframework.security.core.userdetails.UserDetails userDetails = 
                    (org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal();
                
                if (authentication.getDetails() instanceof UUID) {
                    return (UUID) authentication.getDetails();
                }
                
                Object credentials = authentication.getCredentials();
                if (credentials instanceof String) {
                    String token = (String) credentials;
                    return jwtService.extractUserId(token);
                }
            }
            
            if (authentication.getDetails() instanceof UUID) {
                return (UUID) authentication.getDetails();
            }
            
            throw new AccessDeniedException("Unable to extract user ID from authentication");
        } catch (Exception e) {
            logger.error("Error extracting user ID: {}", e.getMessage(), e);
            throw new AccessDeniedException("Invalid authentication token");
        }
    }

    private Object findResourceObject(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        
        if (args == null || args.length == 0) {
            return null;
        }

        for (Object arg : args) {
            if (arg instanceof UUID) {
                return arg;
            }
        }

        return null;
    }
}
