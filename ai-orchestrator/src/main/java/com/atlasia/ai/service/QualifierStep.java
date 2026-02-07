package com.atlasia.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class QualifierStep implements AgentStep {
    private final ObjectMapper objectMapper;

    public QualifierStep(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        String branchName = "ai/issue-" + context.getRunEntity().getIssueNumber();
        context.setBranchName(branchName);

        Map<String, Object> workPlan = Map.of(
            "branchName", branchName,
            "tasks", generateTasks(context),
            "commands", Map.of(
                "backendVerify", "cd ai-orchestrator && mvn clean verify",
                "frontendLint", "cd frontend && npm run lint",
                "frontendTest", "cd frontend && npm test -- --watch=false",
                "e2e", "cd frontend && npm run e2e"
            ),
            "definitionOfDone", List.of(
                "All tests pass",
                "Linting clean",
                "PR created and reviewed"
            )
        );

        return objectMapper.writeValueAsString(workPlan);
    }

    private List<Map<String, Object>> generateTasks(RunContext context) {
        return List.of(
            Map.of(
                "id", "task-1",
                "area", "backend",
                "description", "Implement backend changes",
                "filesLikely", List.of("ai-orchestrator/src/main/java/"),
                "tests", List.of("Unit tests")
            ),
            Map.of(
                "id", "task-2",
                "area", "frontend",
                "description", "Implement frontend changes",
                "filesLikely", List.of("frontend/src/"),
                "tests", List.of("Component tests")
            ),
            Map.of(
                "id", "task-3",
                "area", "docs",
                "description", "Update documentation",
                "filesLikely", List.of("docs/", "README.md"),
                "tests", List.of()
            )
        );
    }
}
