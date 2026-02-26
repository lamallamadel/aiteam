package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GitHubApiClient {
    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private final WebClient webClient;
    private final GitHubAppService gitHubAppService;
    private final OrchestratorProperties properties;
    private final OrchestratorMetrics metrics;
    private final Tracer tracer;

    public GitHubApiClient(GitHubAppService gitHubAppService, OrchestratorProperties properties,
            WebClient.Builder webClientBuilder, OrchestratorMetrics metrics, Tracer tracer,
            @org.springframework.beans.factory.annotation.Value("${atlasia.github.api-url:https://api.github.com}") String githubApiUrl) {
        this.gitHubAppService = gitHubAppService;
        this.properties = properties;
        this.metrics = metrics;
        this.tracer = tracer;

        ConnectionProvider provider = ConnectionProvider.builder("github-pool")
                .maxIdleTime(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .responseTimeout(Duration.ofSeconds(30));

        this.webClient = webClientBuilder
                .baseUrl(githubApiUrl)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    private String getToken() {
        String githubToken = CorrelationIdHolder.getGitHubToken();
        if (StringUtils.hasText(githubToken)) {
            return githubToken;
        }

        String appToken = gitHubAppService.getInstallationToken();
        if (appToken != null) {
            return appToken;
        }
        return properties.token();
    }

    public boolean isValidToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            webClient.get()
                    .uri("/user")
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .toBodilessEntity()
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();
            return true;
        } catch (Exception e) {
            log.warn("Invalid GitHub token provided: {}", e.getMessage());
            return false;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> readIssue(String owner, String repo, int issueNumber) {
        String endpoint = "/repos/" + owner + "/" + repo + "/issues/" + issueNumber;
        Span span = tracer.spanBuilder("github.api.readIssue")
                .setAttribute("http.method", "GET")
                .setAttribute("http.url", endpoint)
                .setAttribute("github.owner", owner)
                .setAttribute("github.repo", repo)
                .setAttribute("github.issue_number", issueNumber)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            Timer.Sample sample = metrics.startGitHubApiTimer();

            try {
                log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

                @SuppressWarnings("unchecked")
                Map<String, Object> response = webClient.get()
                        .uri("/repos/{owner}/{repo}/issues/{issue_number}", owner, repo, issueNumber)
                        .header("Authorization", "Bearer " + getToken())
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("X-Correlation-ID",
                                CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                        : "")
                        .retrieve()
                        .bodyToMono(Map.class)
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                                .filter(this::isTransientError))
                        .block();

                long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
                metrics.recordGitHubApiCall(endpoint, duration);

                span.setStatus(StatusCode.OK);
                span.setAttribute("http.status_code", 200);
                return response;
            } catch (WebClientResponseException e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.setAttribute("http.status_code", e.getStatusCode().value());
                span.recordException(e);
                handleWebClientException(e, endpoint, sample);
                throw e;
            }
        } finally {
            span.end();
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> createBranch(String owner, String repo, String branchName, String sha) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/refs";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = Map.of(
                "ref", "refs/heads/" + branchName,
                "sha", sha);

        try {
            log.debug("GitHub API call: POST {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/refs", owner, repo)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> getReference(String owner, String repo, String ref) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/ref/" + ref;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/git/ref/{ref}", owner, repo, ref)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> createFile(String owner, String repo, String path, String content, String message,
            String branch) {
        validateFilePath(path);

        String endpoint = "/repos/" + owner + "/" + repo + "/contents/" + path;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes());
        Map<String, Object> requestBody = Map.of(
                "message", message,
                "content", encodedContent,
                "branch", branch);

        try {
            log.debug("GitHub API call: PUT {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.put()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    private void validateFilePath(String path) {
        String workflowProtectPrefix = properties.workflowProtectPrefix();
        if (workflowProtectPrefix != null && !workflowProtectPrefix.isEmpty()
                && path.startsWith(workflowProtectPrefix)) {
            throw new IllegalArgumentException("Cannot modify protected workflow files: " + path);
        }

        String allowlist = properties.repoAllowlist();
        if (allowlist != null && !allowlist.isEmpty()) {
            List<String> allowedPrefixes = Arrays.stream(allowlist.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());

            boolean isAllowed = allowedPrefixes.stream().anyMatch(path::startsWith);
            if (!isAllowed) {
                throw new IllegalArgumentException("File path not in allowlist: " + path);
            }
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> createPullRequest(String owner, String repo, String title, String head, String base,
            String body) {
        String endpoint = "/repos/" + owner + "/" + repo + "/pulls";
        Span span = tracer.spanBuilder("github.api.createPullRequest")
                .setAttribute("http.method", "POST")
                .setAttribute("http.url", endpoint)
                .setAttribute("github.owner", owner)
                .setAttribute("github.repo", repo)
                .setAttribute("github.pr.head", head)
                .setAttribute("github.pr.base", base)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            Timer.Sample sample = metrics.startGitHubApiTimer();

            Map<String, Object> requestBody = Map.of(
                    "title", title,
                    "head", head,
                    "base", base,
                    "body", body);

            try {
                log.debug("GitHub API call: POST {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/pulls", owner, repo)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

                long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
                metrics.recordGitHubApiCall(endpoint, duration);

                span.setStatus(StatusCode.OK);
                span.setAttribute("http.status_code", 201);
                Integer prNumber = (Integer) response.get("number");
                if (prNumber != null) {
                    span.setAttribute("github.pr.number", prNumber);
                }
                return response;
            } catch (WebClientResponseException e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.setAttribute("http.status_code", e.getStatusCode().value());
                span.recordException(e);
                handleWebClientException(e, endpoint, sample);
                throw e;
            }
        } finally {
            span.end();
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> getWorkflowRun(String owner, String repo, long runId) {
        String endpoint = "/repos/" + owner + "/" + repo + "/actions/runs/" + runId;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs/{run_id}", owner, repo, runId)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> listWorkflowRunJobs(String owner, String repo, long runId) {
        String endpoint = "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/jobs";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs/{run_id}/jobs", owner, repo, runId)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public String getWorkflowRunLogs(String owner, String repo, long runId) {
        String endpoint = "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/logs";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            String response = webClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs/{run_id}/logs", owner, repo, runId)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public String getJobLogs(String owner, String repo, long jobId) {
        String endpoint = "/repos/" + owner + "/" + repo + "/actions/jobs/" + jobId + "/logs";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            String response = webClient.get()
                    .uri("/repos/{owner}/{repo}/actions/jobs/{job_id}/logs", owner, repo, jobId)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> getPullRequest(String owner, String repo, int pullNumber) {
        String endpoint = "/repos/" + owner + "/" + repo + "/pulls/" + pullNumber;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}", owner, repo, pullNumber)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> listPullRequestCommits(String owner, String repo, int pullNumber) {
        String endpoint = "/repos/" + owner + "/" + repo + "/pulls/" + pullNumber + "/commits";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}/commits", owner, repo, pullNumber)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> getCommitStatus(String owner, String repo, String ref) {
        String endpoint = "/repos/" + owner + "/" + repo + "/commits/" + ref + "/status";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/commits/{ref}/status", owner, repo, ref)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> listCheckRunsForRef(String owner, String repo, String ref) {
        String endpoint = "/repos/" + owner + "/" + repo + "/commits/" + ref + "/check-runs";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/commits/{ref}/check-runs", owner, repo, ref)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public List<Map<String, Object>> listIssueComments(String owner, String repo, int issueNumber) {
        String endpoint = "/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/comments";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("rawtypes")
            List<Map> rawList = webClient.get()
                    .uri("/repos/{owner}/{repo}/issues/{issue_number}/comments", owner, repo, issueNumber)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return (List) rawList;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public void addLabelsToIssue(String owner, String repo, int issueNumber, List<String> labels) {
        String endpoint = "/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/labels";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = Map.of("labels", labels);

        try {
            log.debug("GitHub API call: POST {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            webClient.post()
                    .uri("/repos/{owner}/{repo}/issues/{issue_number}/labels", owner, repo, issueNumber)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> getRepoContent(String owner, String repo, String path) {
        String endpoint = "/repos/" + owner + "/" + repo + "/contents/" + path;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public List<Map<String, Object>> listRepoContents(String owner, String repo, String path) {
        String endpoint = "/repos/" + owner + "/" + repo + "/contents/" + path;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("rawtypes")
            List<Map> rawList = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return (List) rawList;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> getRepoTree(String owner, String repo, String sha, boolean recursive) {
        String recursiveParam = recursive ? "1" : "0";
        String endpoint = "/repos/" + owner + "/" + repo + "/git/trees/" + sha;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/git/trees/{sha}")
                            .queryParam("recursive", recursiveParam)
                            .build(owner, repo, sha))
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> createBlob(String owner, String repo, String content, String encoding) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/blobs";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = Map.of(
                "content", content,
                "encoding", encoding);

        try {
            log.debug("GitHub API call: POST {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/blobs", owner, repo)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> createTree(String owner, String repo, List<Map<String, Object>> tree, String baseTree) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/trees";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("tree", tree);
        if (baseTree != null && !baseTree.isEmpty()) {
            requestBody.put("base_tree", baseTree);
        }

        try {
            log.debug("GitHub API call: POST {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/trees", owner, repo)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> createCommit(String owner, String repo, String message, String tree,
            List<String> parents, Map<String, Object> author, Map<String, Object> committer) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/commits";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("message", message);
        requestBody.put("tree", tree);
        requestBody.put("parents", parents);
        if (author != null) {
            requestBody.put("author", author);
        }
        if (committer != null) {
            requestBody.put("committer", committer);
        }

        try {
            log.debug("GitHub API call: POST {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/commits", owner, repo)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> updateReference(String owner, String repo, String ref, String sha, boolean force) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/refs/" + ref;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = Map.of(
                "sha", sha,
                "force", force);

        try {
            log.debug("GitHub API call: PATCH {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.patch()
                    .uri("/repos/{owner}/{repo}/git/refs/{ref}", owner, repo, ref)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> getCommit(String owner, String repo, String sha) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/commits/" + sha;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/git/commits/{sha}", owner, repo, sha)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> compareCommits(String owner, String repo, String base, String head) {
        String endpoint = "/repos/" + owner + "/" + repo + "/compare/" + base + "..." + head;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/compare/{base}...{head}", owner, repo, base, head)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    private void handleWebClientException(WebClientResponseException e, String endpoint, Timer.Sample sample) {
        if (sample != null) {
            sample.stop(metrics.getGitHubApiDuration());
        }

        int statusCode = e.getStatusCode().value();
        String errorType = determineErrorType(statusCode);

        metrics.recordGitHubApiError(endpoint, errorType);

        if (statusCode == 429 || statusCode == 403) {
            metrics.recordGitHubApiRateLimit(endpoint);
            log.warn("GitHub API rate limit hit: endpoint={}, statusCode={}, correlationId={}",
                    endpoint, statusCode, CorrelationIdHolder.getCorrelationId());
        } else if (statusCode >= 500) {
            log.error("GitHub API server error: endpoint={}, statusCode={}, correlationId={}",
                    endpoint, statusCode, CorrelationIdHolder.getCorrelationId());
        } else {
            log.error("GitHub API client error: endpoint={}, statusCode={}, correlationId={}",
                    endpoint, statusCode, CorrelationIdHolder.getCorrelationId());
        }
    }

    private String determineErrorType(int statusCode) {
        if (statusCode == 429 || statusCode == 403) {
            return "RATE_LIMIT";
        } else if (statusCode >= 500) {
            return "SERVER_ERROR";
        } else if (statusCode == 404) {
            return "NOT_FOUND";
        } else if (statusCode == 401) {
            return "UNAUTHORIZED";
        } else {
            return "CLIENT_ERROR";
        }
    }

    private boolean isTransientError(Throwable throwable) {
        return throwable instanceof WebClientRequestException ||
                (throwable instanceof WebClientResponseException &&
                        ((WebClientResponseException) throwable).getStatusCode().is5xxServerError());
    }

    public Map<String, Object> createPullRequestWithMetadata(
            String owner, String repo, String title, String head, String base,
            String body, Map<String, String> metadata) {
        
        StringBuilder enhancedBody = new StringBuilder(body);
        
        if (metadata != null && !metadata.isEmpty()) {
            enhancedBody.append("\n\n---\n### Multi-Repo Orchestration Metadata\n");
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                enhancedBody.append("- **").append(entry.getKey()).append("**: ")
                           .append(entry.getValue()).append("\n");
            }
        }

        return createPullRequest(owner, repo, title, head, base, enhancedBody.toString());
    }

    public CoordinatedPRResult createCoordinatedPullRequests(List<PRCreationRequest> requests, List<String> mergeOrder) {
        Map<String, PRCreationResult> results = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (PRCreationRequest request : requests) {
            try {
                log.info("Creating coordinated PR: repo={}/{}, branch={}, mergeOrder={}",
                        request.owner(), request.repo(), request.head(), 
                        mergeOrder.indexOf(formatRepoUrl(request.owner(), request.repo())) + 1);

                Map<String, String> metadata = new HashMap<>(request.metadata() != null ? request.metadata() : Map.of());
                metadata.put("Merge Order", String.valueOf(mergeOrder.indexOf(formatRepoUrl(request.owner(), request.repo())) + 1));
                metadata.put("Total PRs", String.valueOf(requests.size()));
                
                if (request.dependencies() != null && !request.dependencies().isEmpty()) {
                    metadata.put("Dependencies", String.join(", ", request.dependencies()));
                }

                Map<String, Object> prResponse = createPullRequestWithMetadata(
                        request.owner(), request.repo(), request.title(),
                        request.head(), request.base(), request.body(), metadata);

                Integer prNumber = (Integer) prResponse.get("number");
                String prUrl = (String) prResponse.get("html_url");

                results.put(formatRepoUrl(request.owner(), request.repo()), 
                           new PRCreationResult(true, prNumber, prUrl, null));

                log.info("Created coordinated PR #{} for {}/{}: {}", 
                        prNumber, request.owner(), request.repo(), prUrl);

            } catch (Exception e) {
                String repoUrl = formatRepoUrl(request.owner(), request.repo());
                String errorMsg = "Failed to create PR for " + repoUrl + ": " + e.getMessage();
                errors.add(errorMsg);
                results.put(repoUrl, new PRCreationResult(false, null, null, errorMsg));
                log.error("Failed to create coordinated PR for {}/{}: {}", 
                         request.owner(), request.repo(), e.getMessage(), e);
            }
        }

        boolean allSuccess = errors.isEmpty();
        return new CoordinatedPRResult(allSuccess, results, mergeOrder, errors);
    }

    @CircuitBreaker(name = "githubApi")
    public Map<String, Object> mergePullRequest(String owner, String repo, int pullNumber, String mergeMethod) {
        String endpoint = "/repos/" + owner + "/" + repo + "/pulls/" + pullNumber + "/merge";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = Map.of("merge_method", mergeMethod);

        try {
            log.debug("GitHub API call: PUT {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.put()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}/merge", owner, repo, pullNumber)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("X-Correlation-ID",
                            CorrelationIdHolder.getCorrelationId() != null ? CorrelationIdHolder.getCorrelationId()
                                    : "")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError))
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    public CoordinatedMergeResult mergeCoordinatedPullRequests(
            Map<String, Integer> repoPrMapping, List<String> mergeOrder, String mergeMethod) {
        
        Map<String, MergeResult> results = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (String repoUrl : mergeOrder) {
            Integer prNumber = repoPrMapping.get(repoUrl);
            if (prNumber == null) {
                log.warn("No PR number found for {} in merge order, skipping", repoUrl);
                continue;
            }

            try {
                String[] parts = parseRepoUrlParts(repoUrl);
                if (parts == null || parts.length < 2) {
                    String errorMsg = "Invalid repository URL: " + repoUrl;
                    errors.add(errorMsg);
                    results.put(repoUrl, new MergeResult(false, null, errorMsg));
                    continue;
                }

                String owner = parts[0];
                String repo = parts[1];

                log.info("Merging coordinated PR #{} for {} (order: {}/{})",
                        prNumber, repoUrl, mergeOrder.indexOf(repoUrl) + 1, mergeOrder.size());

                Map<String, Object> prStatus = getPullRequest(owner, repo, prNumber);
                String state = (String) prStatus.get("state");
                Boolean mergeable = (Boolean) prStatus.get("mergeable");

                if (!"open".equals(state)) {
                    String errorMsg = "PR #" + prNumber + " is not open (state: " + state + ")";
                    errors.add(errorMsg);
                    results.put(repoUrl, new MergeResult(false, null, errorMsg));
                    continue;
                }

                if (Boolean.FALSE.equals(mergeable)) {
                    String errorMsg = "PR #" + prNumber + " has merge conflicts";
                    errors.add(errorMsg);
                    results.put(repoUrl, new MergeResult(false, null, errorMsg));
                    continue;
                }

                Map<String, Object> mergeResponse = mergePullRequest(owner, repo, prNumber, mergeMethod);
                String sha = (String) mergeResponse.get("sha");
                Boolean merged = (Boolean) mergeResponse.get("merged");

                if (Boolean.TRUE.equals(merged)) {
                    results.put(repoUrl, new MergeResult(true, sha, null));
                    log.info("Successfully merged PR #{} for {} with SHA: {}", prNumber, repoUrl, sha);
                } else {
                    String errorMsg = "Merge request accepted but merged=false";
                    errors.add(errorMsg);
                    results.put(repoUrl, new MergeResult(false, null, errorMsg));
                }

            } catch (Exception e) {
                String errorMsg = "Failed to merge PR #" + prNumber + " for " + repoUrl + ": " + e.getMessage();
                errors.add(errorMsg);
                results.put(repoUrl, new MergeResult(false, null, errorMsg));
                log.error("Failed to merge coordinated PR for {}: {}", repoUrl, e.getMessage(), e);
            }
        }

        boolean allSuccess = errors.isEmpty();
        return new CoordinatedMergeResult(allSuccess, results, errors);
    }

    private String formatRepoUrl(String owner, String repo) {
        return String.format("github.com/%s/%s", owner.toLowerCase(), repo.toLowerCase());
    }

    private String[] parseRepoUrlParts(String repoUrl) {
        String normalized = repoUrl.toLowerCase()
                .replaceFirst("^https?://", "")
                .replaceFirst("\\.git$", "")
                .replaceFirst("/$", "");

        String[] parts = normalized.split("/");
        if (parts.length >= 3 && parts[0].contains("github.com")) {
            return new String[] { parts[1], parts[2] };
        } else if (parts.length >= 2) {
            return new String[] { parts[0], parts[1] };
        }
        return null;
    }

    public record PRCreationRequest(
        String owner,
        String repo,
        String title,
        String head,
        String base,
        String body,
        List<String> dependencies,
        Map<String, String> metadata
    ) {}

    public record PRCreationResult(
        boolean success,
        Integer prNumber,
        String prUrl,
        String error
    ) {}

    public record CoordinatedPRResult(
        boolean allSuccess,
        Map<String, PRCreationResult> results,
        List<String> mergeOrder,
        List<String> errors
    ) {}

    public record MergeResult(
        boolean success,
        String sha,
        String error
    ) {}

    public record CoordinatedMergeResult(
        boolean allSuccess,
        Map<String, MergeResult> results,
        List<String> errors
    ) {}
}
