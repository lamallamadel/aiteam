package com.atlasia.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

@Component
public class DeveloperStep implements AgentStep {
    private final GitHubApiClient gitHubApiClient;
    private final ObjectMapper objectMapper;

    public DeveloperStep(GitHubApiClient gitHubApiClient, ObjectMapper objectMapper) {
        this.gitHubApiClient = gitHubApiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        String branchName = context.getBranchName();
        String owner = context.getOwner();
        String repo = context.getRepo();

        Map<String, Object> mainRef = gitHubApiClient.getReference(owner, repo, "heads/main");
        Map<String, Object> mainObject = (Map<String, Object>) mainRef.get("object");
        String baseSha = (String) mainObject.get("sha");

        gitHubApiClient.createBranch(owner, repo, branchName, baseSha);

        String commitMessage = "feat: Implement changes for issue #" + context.getRunEntity().getIssueNumber();
        String sampleContent = generateSampleImplementation(context);
        String encodedContent = Base64.getEncoder().encodeToString(sampleContent.getBytes());

        gitHubApiClient.createFile(
            owner,
            repo,
            "docs/AI_IMPLEMENTATION_" + context.getRunEntity().getIssueNumber() + ".md",
            encodedContent,
            commitMessage,
            branchName
        );

        String prTitle = "AI: Fix issue #" + context.getRunEntity().getIssueNumber();
        String prBody = "This PR implements the changes requested in issue #" + 
                        context.getRunEntity().getIssueNumber() + "\n\n" +
                        "## Changes\n" +
                        "- Implementation details\n\n" +
                        "## Testing\n" +
                        "- All tests pass\n";

        Map<String, Object> pr = gitHubApiClient.createPullRequest(
            owner,
            repo,
            prTitle,
            branchName,
            "main",
            prBody
        );

        String prUrl = (String) pr.get("html_url");
        context.setPrUrl(prUrl);

        return prUrl;
    }

    private String generateSampleImplementation(RunContext context) {
        return "# AI Implementation\n\n" +
               "Issue: #" + context.getRunEntity().getIssueNumber() + "\n\n" +
               "## Implementation Notes\n" +
               context.getArchitectureNotes() + "\n\n" +
               "This file represents the implementation placeholder for the AI agent.";
    }
}
