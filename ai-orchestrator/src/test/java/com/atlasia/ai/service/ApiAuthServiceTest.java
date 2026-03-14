package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiAuthServiceTest {

    @Mock private OrchestratorProperties orchestratorProperties;
    @Mock private GitHubApiClient gitHubApiClient;

    private ApiAuthService service;

    @BeforeEach
    void setUp() {
        service = new ApiAuthService(orchestratorProperties, gitHubApiClient);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isAuthorized_emptyHeader_returnsFalse() {
        lenient().when(orchestratorProperties.token()).thenReturn("admin-secret");
        assertFalse(service.isAuthorized(null));
        assertFalse(service.isAuthorized(""));
        assertFalse(service.isAuthorized("   "));
    }

    @Test
    void isAuthorized_noBearerPrefix_returnsFalse() {
        lenient().when(orchestratorProperties.token()).thenReturn("admin-secret");
        assertFalse(service.isAuthorized("Basic foo"));
        assertFalse(service.isAuthorized("admin-secret"));
    }

    @Test
    void isAuthorized_adminToken_returnsTrue() {
        when(orchestratorProperties.token()).thenReturn("admin-secret");
        assertTrue(service.isAuthorized("Bearer admin-secret"));
        assertTrue(service.isAuthorized("Bearer  admin-secret  "));
    }

    @Test
    void isAuthorized_adminTokenEmptyConfig_returnsFalse() {
        when(orchestratorProperties.token()).thenReturn(null);
        assertFalse(service.isAuthorized("Bearer any-token"));

        when(orchestratorProperties.token()).thenReturn("");
        assertFalse(service.isAuthorized("Bearer any-token"));
    }

    @Test
    void isAuthorized_validGitHubToken_returnsTrue() {
        when(orchestratorProperties.token()).thenReturn("other");
        when(gitHubApiClient.isValidToken("gh-token-123")).thenReturn(true);

        assertTrue(service.isAuthorized("Bearer gh-token-123"));
    }

    @Test
    void isAuthorized_invalidGitHubToken_returnsFalse() {
        when(orchestratorProperties.token()).thenReturn("other");
        when(gitHubApiClient.isValidToken("gh-invalid")).thenReturn(false);

        assertFalse(service.isAuthorized("Bearer gh-invalid"));
    }

    @Test
    void isAuthorized_securityContextAuthenticated_returnsTrue() {
        when(orchestratorProperties.token()).thenReturn(null);
        when(gitHubApiClient.isValidToken("jwt-token")).thenReturn(false);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.Collections.emptyList()));

        assertTrue(service.isAuthorized("Bearer jwt-token"));
    }

    @Test
    void isAuthorized_securityContextAnonymous_returnsFalse() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, java.util.Collections.emptyList()));

        when(orchestratorProperties.token()).thenReturn(null);
        assertFalse(service.isAuthorized("Bearer unknown-token"));
    }

    @Test
    void isAuthorized_noHeaderButSecurityContext_returnsTrue() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.Collections.emptyList()));

        lenient().when(orchestratorProperties.token()).thenReturn(null);
        assertTrue(service.isAuthorized(null));
    }

    @Test
    void getApiTokenForWorkflow_adminToken_returnsToken() {
        when(orchestratorProperties.token()).thenReturn("admin-secret");
        var result = service.getApiTokenForWorkflow("Bearer admin-secret");
        assertTrue(result.isPresent());
        assertEquals("admin-secret", result.get());
    }

    @Test
    void getApiTokenForWorkflow_githubToken_returnsToken() {
        when(orchestratorProperties.token()).thenReturn("other");
        when(gitHubApiClient.isValidToken("gh-valid")).thenReturn(true);
        var result = service.getApiTokenForWorkflow("Bearer gh-valid");
        assertTrue(result.isPresent());
        assertEquals("gh-valid", result.get());
    }

    @Test
    void getApiTokenForWorkflow_jwtOnly_returnsEmpty() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.Collections.emptyList()));
        when(orchestratorProperties.token()).thenReturn(null);
        when(gitHubApiClient.isValidToken("jwt-token")).thenReturn(false);

        var result = service.getApiTokenForWorkflow("Bearer jwt-token");
        assertTrue(result.isEmpty());
    }

    @Test
    void getApiTokenForWorkflow_emptyHeader_returnsEmpty() {
        assertTrue(service.getApiTokenForWorkflow(null).isEmpty());
        assertTrue(service.getApiTokenForWorkflow("").isEmpty());
    }

    @Test
    void isAdminToken_adminToken_returnsTrue() {
        when(orchestratorProperties.token()).thenReturn("admin-secret");
        assertTrue(service.isAdminToken("Bearer admin-secret"));
    }

    @Test
    void isAdminToken_githubToken_returnsFalse() {
        lenient().when(orchestratorProperties.token()).thenReturn("admin-secret");
        lenient().when(gitHubApiClient.isValidToken("gh-token")).thenReturn(true);
        assertFalse(service.isAdminToken("Bearer gh-token"));
    }

    @Test
    void isAdminToken_invalidToken_returnsFalse() {
        when(orchestratorProperties.token()).thenReturn("admin-secret");
        assertFalse(service.isAdminToken("Bearer wrong-token"));
        assertFalse(service.isAdminToken(null));
    }
}
