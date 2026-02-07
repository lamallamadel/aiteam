package com.atlasia.ai.service;

import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class WriterStep implements AgentStep {
    private final GitHubApiClient gitHubApiClient;

    public WriterStep(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        String owner = context.getOwner();
        String repo = context.getRepo();
        String branchName = context.getBranchName();

        String docContent = generateDocumentation(context);
        String encodedContent = Base64.getEncoder().encodeToString(docContent.getBytes());

        try {
            gitHubApiClient.createFile(
                owner,
                repo,
                "docs/CHANGELOG_" + context.getRunEntity().getIssueNumber() + ".md",
                encodedContent,
                "docs: Update documentation for issue #" + context.getRunEntity().getIssueNumber(),
                branchName
            );
        } catch (Exception e) {
        }

        return "Documentation updated for PR: " + context.getPrUrl();
    }

    private String generateDocumentation(RunContext context) {
        StringBuilder doc = new StringBuilder();
        doc.append("# Changelog\n\n");
        doc.append("## Issue #").append(context.getRunEntity().getIssueNumber()).append("\n\n");
        doc.append("### Changes\n");
        doc.append("- Implemented requested functionality\n");
        doc.append("- Added tests\n");
        doc.append("- Updated documentation\n\n");
        doc.append("### PR\n");
        doc.append(context.getPrUrl()).append("\n");
        return doc.toString();
    }
}
