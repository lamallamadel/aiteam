package com.atlasia.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PmStep implements AgentStep {
    private final GitHubApiClient gitHubApiClient;
    private final ObjectMapper objectMapper;

    public PmStep(GitHubApiClient gitHubApiClient, ObjectMapper objectMapper) {
        this.gitHubApiClient = gitHubApiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        Map<String, Object> issueData = gitHubApiClient.readIssue(
            context.getOwner(), 
            context.getRepo(), 
            context.getRunEntity().getIssueNumber()
        );
        context.setIssueData(issueData);

        String title = (String) issueData.get("title");
        String body = (String) issueData.getOrDefault("body", "");
        
        Map<String, Object> ticketPlan = Map.of(
            "issueId", context.getRunEntity().getIssueNumber(),
            "title", title,
            "summary", generateSummary(title, body),
            "acceptanceCriteria", extractAcceptanceCriteria(body),
            "outOfScope", List.of(),
            "risks", extractRisks(body),
            "labelsToApply", List.of("ai-generated")
        );

        return objectMapper.writeValueAsString(ticketPlan);
    }

    private String generateSummary(String title, String body) {
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private List<String> extractAcceptanceCriteria(String body) {
        return List.of(
            "Implementation matches issue requirements",
            "All tests pass",
            "Code follows project conventions"
        );
    }

    private List<String> extractRisks(String body) {
        return List.of();
    }
}
