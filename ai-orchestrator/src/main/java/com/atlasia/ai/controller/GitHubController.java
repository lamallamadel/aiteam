package com.atlasia.ai.controller;

import com.atlasia.ai.service.ApiAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Lightweight proxy for GitHub API calls needed by the frontend (repo listing, validation).
 * Does NOT use GitHubApiClient (which uses the server-level token) — it forwards the user's PAT directly.
 */
@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private static final Logger log = LoggerFactory.getLogger(GitHubController.class);

    private final WebClient webClient;
    private final ApiAuthService apiAuthService;

    public GitHubController(ApiAuthService apiAuthService) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
        this.apiAuthService = apiAuthService;
    }

    /**
     * Returns the list of repositories accessible with the given GitHub PAT.
     * The token is passed as a query parameter so it never touches server-side storage.
     * Called once at onboarding / settings save; result is cached client-side.
     */
    @GetMapping("/repos")
    @PreAuthorize("hasRole('USER')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<List<Map<String, Object>>> listRepos(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("token") String githubToken) {

        if (!apiAuthService.isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!StringUtils.hasText(githubToken)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            List<Map<String, Object>> repos = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/repos")
                            .queryParam("per_page", 100)
                            .queryParam("sort", "updated")
                            .queryParam("affiliation", "owner,collaborator,organization_member")
                            .build())
                    .header("Authorization", "Bearer " + githubToken)
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .map(m -> (Map<String, Object>) m)
                    .collectList()
                    .block();

            return ResponseEntity.ok(repos != null ? repos : List.of());
        } catch (WebClientResponseException e) {
            log.warn("GitHub /user/repos returned {}: {}", e.getStatusCode(), e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("Error fetching GitHub repos", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /**
     * Validates a GitHub PAT by calling GET /user.
     * Returns 200 with the GitHub user profile, or 401 if invalid.
     */
    @GetMapping("/validate")
    @PreAuthorize("hasRole('USER')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("token") String githubToken) {

        if (!apiAuthService.isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!StringUtils.hasText(githubToken)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Map<String, Object> user = webClient.get()
                    .uri("/user")
                    .header("Authorization", "Bearer " + githubToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(m -> (Map<String, Object>) m)
                    .block();

            return ResponseEntity.ok(user != null ? user : Map.of());
        } catch (WebClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("Error validating GitHub token", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
