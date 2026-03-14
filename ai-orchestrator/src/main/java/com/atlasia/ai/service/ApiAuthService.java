package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Central service for API authentication decisions.
 * Unifies JWT (via SecurityContext), admin token, and GitHub token validation.
 * Used by RunController, GraftController, MultiRepoController, A2AController.
 *
 * Keycloak-ready: when BearerTokenAuthenticationResolver is switched to Keycloak,
 * SecurityContext will be filled by Keycloak JWT and ApiAuthService continues to work.
 */
@Service
public class ApiAuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final OrchestratorProperties orchestratorProperties;
    private final GitHubApiClient gitHubApiClient;

    public ApiAuthService(OrchestratorProperties orchestratorProperties, GitHubApiClient gitHubApiClient) {
        this.orchestratorProperties = orchestratorProperties;
        this.gitHubApiClient = gitHubApiClient;
    }

    /**
     * Returns true if the request is authorized: admin token, valid GitHub token, or
     * JWT-authenticated user (SecurityContext already set by JwtAuthenticationFilter).
     */
    public boolean isAuthorized(String authorizationHeader) {
        Optional<String> apiToken = extractApiToken(authorizationHeader);
        if (apiToken.isPresent()) {
            String token = apiToken.get();
            if (isAdminTokenValue(token)) return true;
            if (gitHubApiClient.isValidToken(token)) return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
    }

    /**
     * Returns the token for workflow/CI use only when it is admin or GitHub token.
     * For JWT-authenticated users, returns empty (workflow receives null).
     */
    public Optional<String> getApiTokenForWorkflow(String authorizationHeader) {
        Optional<String> apiToken = extractApiToken(authorizationHeader);
        if (apiToken.isEmpty()) return Optional.empty();

        String token = apiToken.get();
        if (isAdminTokenValue(token)) return Optional.of(token);
        if (gitHubApiClient.isValidToken(token)) return Optional.of(token);

        return Optional.empty();
    }

    /**
     * Returns true only when the Bearer token is the configured admin token.
     */
    public boolean isAdminToken(String authorizationHeader) {
        Optional<String> token = extractApiToken(authorizationHeader);
        return token.filter(this::isAdminTokenValue).isPresent();
    }

    private Optional<String> extractApiToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? Optional.of(token) : Optional.empty();
    }

    private boolean isAdminTokenValue(String token) {
        return StringUtils.hasText(orchestratorProperties.token())
                && orchestratorProperties.token().equals(token);
    }
}
