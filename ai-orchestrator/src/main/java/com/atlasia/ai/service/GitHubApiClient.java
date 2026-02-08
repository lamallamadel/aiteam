package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.service.exception.GitHubApiException;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GitHubApiClient {
    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private final WebClient webClient;
    private final GitHubAppService gitHubAppService;
    private final OrchestratorProperties properties;
    private final OrchestratorMetrics metrics;

    public GitHubApiClient(GitHubAppService gitHubAppService, OrchestratorProperties properties,
            WebClient.Builder webClientBuilder, OrchestratorMetrics metrics) {
        this.gitHubAppService = gitHubAppService;
        this.properties = properties;
        this.metrics = metrics;
        this.webClient = webClientBuilder.baseUrl("https://api.github.com").build();
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
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (Exception e) {
            log.warn("Invalid GitHub token provided: {}", e.getMessage());
            return false;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> readIssue(String owner, String repo, int issueNumber) {
        String endpoint = "/repos/" + owner + "/" + repo + "/issues/" + issueNumber;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/issues/{issue_number}", owner, repo, issueNumber)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> createBranch(String owner, String repo, String branchName, String sha) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/refs";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = Map.of(
                "ref", "refs/heads/" + branchName,
                "sha", sha);

        try {
            log.debug("GitHub API call: POST {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/refs", owner, repo)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> getReference(String owner, String repo, String ref) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/ref/" + ref;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/git/ref/{ref}", owner, repo, ref)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> createFile(String owner, String repo, String path, String content, String message,
            String branch) {
        validateFilePath(path);

        String endpoint = "/repos/" + owner + "/" + repo + "/contents/" + path;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = Map.of(
                "message", message,
                "content", content,
                "branch", branch);

        try {
            log.debug("GitHub API call: PUT {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.put()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
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

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> createPullRequest(String owner, String repo, String title, String head, String base,
            String body) {
        String endpoint = "/repos/" + owner + "/" + repo + "/pulls";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = Map.of(
                "title", title,
                "head", head,
                "base", base,
                "body", body);

        try {
            log.debug("GitHub API call: POST {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/pulls", owner, repo)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> getWorkflowRun(String owner, String repo, long runId) {
        String endpoint = "/repos/" + owner + "/" + repo + "/actions/runs/" + runId;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs/{run_id}", owner, repo, runId)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> listWorkflowRunJobs(String owner, String repo, long runId) {
        String endpoint = "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/jobs";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs/{run_id}/jobs", owner, repo, runId)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
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
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
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
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> getPullRequest(String owner, String repo, int pullNumber) {
        String endpoint = "/repos/" + owner + "/" + repo + "/pulls/" + pullNumber;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}", owner, repo, pullNumber)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> listPullRequestCommits(String owner, String repo, int pullNumber) {
        String endpoint = "/repos/" + owner + "/" + repo + "/pulls/" + pullNumber + "/commits";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}/commits", owner, repo, pullNumber)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> getCommitStatus(String owner, String repo, String ref) {
        String endpoint = "/repos/" + owner + "/" + repo + "/commits/" + ref + "/status";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/commits/{ref}/status", owner, repo, ref)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> listCheckRunsForRef(String owner, String repo, String ref) {
        String endpoint = "/repos/" + owner + "/" + repo + "/commits/" + ref + "/check-runs";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/commits/{ref}/check-runs", owner, repo, ref)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public List<Map<String, Object>> listIssueComments(String owner, String repo, int issueNumber) {
        String endpoint = "/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/comments";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            List<Map> rawList = webClient.get()
                    .uri("/repos/{owner}/{repo}/issues/{issue_number}/comments", owner, repo, issueNumber)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return (List) rawList;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
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
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> getRepoContent(String owner, String repo, String path) {
        String endpoint = "/repos/" + owner + "/" + repo + "/contents/" + path;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public List<Map<String, Object>> listRepoContents(String owner, String repo, String path) {
        String endpoint = "/repos/" + owner + "/" + repo + "/contents/" + path;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            List<Map> rawList = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return (List) rawList;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> getRepoTree(String owner, String repo, String sha, boolean recursive) {
        String recursiveParam = recursive ? "1" : "0";
        String endpoint = "/repos/" + owner + "/" + repo + "/git/trees/" + sha;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/git/trees/{sha}")
                            .queryParam("recursive", recursiveParam)
                            .build(owner, repo, sha))
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> createBlob(String owner, String repo, String content, String encoding) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/blobs";
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = Map.of(
                "content", content,
                "encoding", encoding);

        try {
            log.debug("GitHub API call: POST {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/blobs", owner, repo)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
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

            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/trees", owner, repo)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
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

            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/commits", owner, repo)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> updateReference(String owner, String repo, String ref, String sha, boolean force) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/refs/" + ref;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        Map<String, Object> requestBody = Map.of(
                "sha", sha,
                "force", force);

        try {
            log.debug("GitHub API call: PATCH {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.patch()
                    .uri("/repos/{owner}/{repo}/git/refs/{ref}", owner, repo, ref)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> getCommit(String owner, String repo, String sha) {
        String endpoint = "/repos/" + owner + "/" + repo + "/git/commits/" + sha;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/git/commits/{sha}", owner, repo, sha)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long duration = sample.stop(metrics.getGitHubApiDuration()) / 1_000_000;
            metrics.recordGitHubApiCall(endpoint, duration);

            return response;
        } catch (WebClientResponseException e) {
            handleWebClientException(e, endpoint, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fallbackMethod")
    public Map<String, Object> compareCommits(String owner, String repo, String base, String head) {
        String endpoint = "/repos/" + owner + "/" + repo + "/compare/" + base + "..." + head;
        Timer.Sample sample = metrics.startGitHubApiTimer();

        try {
            log.debug("GitHub API call: GET {}, correlationId={}", endpoint, CorrelationIdHolder.getCorrelationId());

            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/compare/{base}...{head}", owner, repo, base, head)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
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

    private Map<String, Object> fallbackMethod(Exception e) {
        log.error("GitHub API circuit breaker fallback triggered, correlationId={}",
                CorrelationIdHolder.getCorrelationId(), e);
        throw new GitHubApiException("GitHub API circuit breaker open or fallback triggered", e.getMessage(), 503);
    }

    private String fallbackMethodString(Exception e) {
        log.error("GitHub API circuit breaker fallback triggered, correlationId={}",
                CorrelationIdHolder.getCorrelationId(), e);
        throw new GitHubApiException("GitHub API circuit breaker open or fallback triggered", e.getMessage(), 503);
    }

    private List<Map<String, Object>> fallbackMethodList(Exception e) {
        log.error("GitHub API circuit breaker fallback triggered, correlationId={}",
                CorrelationIdHolder.getCorrelationId(), e);
        throw new GitHubApiException("GitHub API circuit breaker open or fallback triggered", e.getMessage(), 503);
    }

    private void fallbackMethodVoid(Exception e) {
        log.error("GitHub API circuit breaker fallback triggered, correlationId={}",
                CorrelationIdHolder.getCorrelationId(), e);
        throw new GitHubApiException("GitHub API circuit breaker open or fallback triggered", e.getMessage(), 503);
    }
}
