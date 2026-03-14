package com.atlasia.ai.service.auth;

import com.atlasia.ai.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;

/**
 * Resolves Bearer tokens using local JWT validation (symmetric key + UserDetails).
 * Supports the E2E test token when enabled via configuration.
 */
@Component
public class LocalJwtBearerTokenResolver implements BearerTokenAuthenticationResolver {

    private static final String E2E_TEST_TOKEN = "test-github-token";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final boolean e2eTestTokenEnabled;

    public LocalJwtBearerTokenResolver(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            @Value("${atlasia.auth.e2e-test-token-enabled:false}") boolean e2eTestTokenEnabled) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.e2eTestTokenEnabled = e2eTestTokenEnabled;
    }

    @Override
    public Optional<Authentication> resolve(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }

        // E2E test token (only when explicitly enabled)
        if (e2eTestTokenEnabled && E2E_TEST_TOKEN.equals(bearerToken)) {
            return Optional.of(new UsernamePasswordAuthenticationToken(
                    "test-agent", null, Collections.emptyList()));
        }

        // Validate JWT and load user
        if (!jwtService.validateToken(bearerToken)) {
            return Optional.empty();
        }

        try {
            String username = jwtService.extractUsername(bearerToken);
            if (username == null) return Optional.empty();

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
            authentication.setDetails(jwtService.extractUserId(bearerToken));
            return Optional.of(authentication);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
