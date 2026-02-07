package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GitHubApiClient {
    private final WebClient webClient;
    private final GitHubAppService gitHubAppService;
    private final OrchestratorProperties properties;

    public GitHubApiClient(GitHubAppService gitHubAppService, OrchestratorProperties properties, WebClient.Builder webClientBuilder) {
        this.gitHubAppService = gitHubAppService;
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl("https://api.github.com").build();
    }

    private String getToken() {
        return gitHubAppService.getInstallationToken();
    }

    public Map<String, Object> readIssue(String owner, String repo, int issueNumber) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/issues/{issue_number}", owner, repo, issueNumber)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> createBranch(String owner, String repo, String branchName, String sha) {
        Map<String, Object> requestBody = Map.of(
                "ref", "refs/heads/" + branchName,
                "sha", sha
        );

        return webClient.post()
                .uri("/repos/{owner}/{repo}/git/refs", owner, repo)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> getReference(String owner, String repo, String ref) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/git/ref/{ref}", owner, repo, ref)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> commitFile(String owner, String repo, String path, String content, String message, String branch, String sha) {
        validateFilePath(path);

        Map<String, Object> requestBody = Map.of(
                "message", message,
                "content", content,
                "branch", branch,
                "sha", sha
        );

        return webClient.put()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> createFile(String owner, String repo, String path, String content, String message, String branch) {
        validateFilePath(path);

        Map<String, Object> requestBody = Map.of(
                "message", message,
                "content", content,
                "branch", branch
        );

        return webClient.put()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    private void validateFilePath(String path) {
        String workflowProtectPrefix = properties.workflowProtectPrefix();
        if (workflowProtectPrefix != null && !workflowProtectPrefix.isEmpty() && path.startsWith(workflowProtectPrefix)) {
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

    public Map<String, Object> createPullRequest(String owner, String repo, String title, String head, String base, String body) {
        Map<String, Object> requestBody = Map.of(
                "title", title,
                "head", head,
                "base", base,
                "body", body
        );

        return webClient.post()
                .uri("/repos/{owner}/{repo}/pulls", owner, repo)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> getWorkflowRun(String owner, String repo, long runId) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/actions/runs/{run_id}", owner, repo, runId)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> listWorkflowRunJobs(String owner, String repo, long runId) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/actions/runs/{run_id}/jobs", owner, repo, runId)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public String getWorkflowRunLogs(String owner, String repo, long runId) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/actions/runs/{run_id}/logs", owner, repo, runId)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getJobLogs(String owner, String repo, long jobId) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/actions/jobs/{job_id}/logs", owner, repo, jobId)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public Map<String, Object> getPullRequest(String owner, String repo, int pullNumber) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{pull_number}", owner, repo, pullNumber)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> listPullRequestCommits(String owner, String repo, int pullNumber) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{pull_number}/commits", owner, repo, pullNumber)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> getCommitStatus(String owner, String repo, String ref) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/commits/{ref}/status", owner, repo, ref)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> listCheckRunsForRef(String owner, String repo, String ref) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/commits/{ref}/check-runs", owner, repo, ref)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public List<Map<String, Object>> listIssueComments(String owner, String repo, int issueNumber) {
        List<Map> rawList = webClient.get()
                .uri("/repos/{owner}/{repo}/issues/{issue_number}/comments", owner, repo, issueNumber)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToFlux(Map.class)
                .collectList()
                .block();
        return (List) rawList;
    }

    public void addLabelsToIssue(String owner, String repo, int issueNumber, List<String> labels) {
        Map<String, Object> requestBody = Map.of("labels", labels);

        webClient.post()
                .uri("/repos/{owner}/{repo}/issues/{issue_number}/labels", owner, repo, issueNumber)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public Map<String, Object> getRepoContent(String owner, String repo, String path) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public List<Map<String, Object>> listRepoContents(String owner, String repo, String path) {
        List<Map> rawList = webClient.get()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToFlux(Map.class)
                .collectList()
                .block();
        return (List) rawList;
    }

    public Map<String, Object> getRepoTree(String owner, String repo, String sha, boolean recursive) {
        String recursiveParam = recursive ? "1" : "0";
        return webClient.get()
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
    }

    public Map<String, Object> createBlob(String owner, String repo, String content, String encoding) {
        Map<String, Object> requestBody = Map.of(
                "content", content,
                "encoding", encoding
        );

        return webClient.post()
                .uri("/repos/{owner}/{repo}/git/blobs", owner, repo)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> createTree(String owner, String repo, List<Map<String, Object>> tree, String baseTree) {
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("tree", tree);
        if (baseTree != null && !baseTree.isEmpty()) {
            requestBody.put("base_tree", baseTree);
        }

        return webClient.post()
                .uri("/repos/{owner}/{repo}/git/trees", owner, repo)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> createCommit(String owner, String repo, String message, String tree, List<String> parents, Map<String, Object> author, Map<String, Object> committer) {
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

        return webClient.post()
                .uri("/repos/{owner}/{repo}/git/commits", owner, repo)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> updateReference(String owner, String repo, String ref, String sha, boolean force) {
        Map<String, Object> requestBody = Map.of(
                "sha", sha,
                "force", force
        );

        return webClient.patch()
                .uri("/repos/{owner}/{repo}/git/refs/{ref}", owner, repo, ref)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> getCommit(String owner, String repo, String sha) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/git/commits/{sha}", owner, repo, sha)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> compareCommits(String owner, String repo, String base, String head) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/compare/{base}...{head}", owner, repo, base, head)
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}
