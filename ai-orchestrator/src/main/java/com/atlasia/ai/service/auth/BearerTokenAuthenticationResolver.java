package com.atlasia.ai.service.auth;

import org.springframework.security.core.Authentication;

import java.util.Optional;

/**
 * Abstraction for resolving a Bearer token string into an Authentication.
 * Allows swapping between local JWT validation and Keycloak/OIDC validation.
 *
 * Implementations:
 * - LocalJwtBearerTokenResolver: current symmetric JWT + UserDetails
 * - KeycloakJwtBearerTokenResolver (future): OIDC JWKS + claim mapping
 */
public interface BearerTokenAuthenticationResolver {

    /**
     * Resolves the given Bearer token (without "Bearer " prefix) to an Authentication.
     *
     * @param bearerToken the raw token from the Authorization header
     * @return Optional containing Authentication if valid, empty otherwise
     */
    Optional<Authentication> resolve(String bearerToken);
}
