package com.atlasia.ai.service;

import com.atlasia.ai.model.TicketPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class QualifierStep implements AgentStep {
    private static final Logger log = LoggerFactory.getLogger(QualifierStep.class);

    private final ObjectMapper objectMapper;
    private final LlmService llmService;
    private final GitHubApiClient gitHubApiClient;

    public QualifierStep(ObjectMapper objectMapper, LlmService llmService, GitHubApiClient gitHubApiClient) {
        this.objectMapper = objectMapper;
        this.llmService = llmService;
        this.gitHubApiClient = gitHubApiClient;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        String branchName = "ai/issue-" + context.getRunEntity().getIssueNumber();
        context.setBranchName(branchName);

        TicketPlan ticketPlan = parseTicketPlan(context.getTicketPlan());
        RepoStructure repoStructure = analyzeRepoStructure(context);
        AgentsMdInfo agentsMdInfo = parseAgentsMd(context);

        Map<String, Object> workPlan = generateWorkPlanWithLlm(
                context,
                branchName,
                ticketPlan,
                repoStructure,
                agentsMdInfo);

        validateWorkPlan(workPlan);

        return objectMapper.writeValueAsString(workPlan);
    }

    private TicketPlan parseTicketPlan(String ticketPlanJson) throws JsonProcessingException {
        return objectMapper.readValue(ticketPlanJson, TicketPlan.class);
    }

    private RepoStructure analyzeRepoStructure(RunContext context) {
        log.info("Analyzing repository structure for {}/{}", context.getOwner(), context.getRepo());

        RepoStructure structure = new RepoStructure();

        try {
            Map<String, Object> defaultBranchRef = gitHubApiClient.getReference(
                    context.getOwner(),
                    context.getRepo(),
                    "heads/main");

            Map<String, Object> objectData = (Map<String, Object>) defaultBranchRef.get("object");
            String sha = (String) objectData.get("sha");

            Map<String, Object> tree = gitHubApiClient.getRepoTree(
                    context.getOwner(),
                    context.getRepo(),
                    sha,
                    true);

            List<Map<String, Object>> treeItems = (List<Map<String, Object>>) tree.get("tree");

            for (Map<String, Object> item : treeItems) {
                String path = (String) item.get("path");
                String type = (String) item.get("type");

                if ("blob".equals(type)) {
                    structure.addFile(path);

                    if (path.endsWith(".java")) {
                        structure.addBackendFile(path);
                    } else if (path.endsWith(".ts") || path.endsWith(".js") ||
                            path.endsWith(".html") || path.endsWith(".css") ||
                            path.endsWith(".scss")) {
                        structure.addFrontendFile(path);
                    } else if (path.contains("test") || path.contains("Test") ||
                            path.contains("spec") || path.contains("Spec")) {
                        structure.addTestFile(path);
                    } else if (path.endsWith(".md") || path.startsWith("docs/")) {
                        structure.addDocFile(path);
                    } else if (path.startsWith("infra/") || path.contains("docker") ||
                            path.contains("Dockerfile") || path.endsWith(".yml") ||
                            path.endsWith(".yaml")) {
                        structure.addInfraFile(path);
                    }
                }
            }

            log.info("Repository structure analyzed: {} total files, {} backend, {} frontend, {} tests",
                    structure.getAllFiles().size(),
                    structure.getBackendFiles().size(),
                    structure.getFrontendFiles().size(),
                    structure.getTestFiles().size());

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            if (e.getStatusCode().value() == 404 || e.getStatusCode().value() == 409) {
                log.warn("Repository appears to be empty or default branch not found: {}/{} ({})",
                        context.getOwner(), context.getRepo(), e.getStatusCode());
            } else {
                log.error("GitHub API error while analyzing repo structure: {}/{} - {}",
                        context.getOwner(), context.getRepo(), e.getMessage());
            }
            structure.addBackendFile("src/main/java/");
            structure.addFrontendFile("src/");
            structure.addDocFile("README.md");
        } catch (Exception e) {
            log.warn("Failed to fetch full repository tree, using fallback: {}", e.getMessage());
            structure.addBackendFile("ai-orchestrator/src/main/java/");
            structure.addFrontendFile("frontend/src/");
            structure.addTestFile("ai-orchestrator/src/test/java/");
            structure.addDocFile("docs/");
        }

        return structure;
    }

    private AgentsMdInfo parseAgentsMd(RunContext context) {
        log.info("Parsing AGENTS.md for command information");

        AgentsMdInfo info = new AgentsMdInfo();

        try {
            Map<String, Object> agentsMdContent = gitHubApiClient.getRepoContent(
                    context.getOwner(),
                    context.getRepo(),
                    "AGENTS.md");

            String contentBase64 = (String) agentsMdContent.get("content");
            byte[] decodedBytes = Base64.getDecoder().decode(contentBase64.replaceAll("\\s", ""));
            String content = new String(decodedBytes);

            info.parseFromContent(content);

            log.info("AGENTS.md parsed successfully");

        } catch (Exception e) {
            log.warn("Failed to fetch AGENTS.md, using default commands: {}", e.getMessage());
            info.setBackendBuildCommand("cd ai-orchestrator && mvn clean verify");
            info.setBackendTestCommand("cd ai-orchestrator && mvn test");
            info.setFrontendLintCommand("cd frontend && npm run lint");
            info.setFrontendTestCommand("cd frontend && npm test -- --watch=false");
            info.setE2eCommand("cd frontend && npm run e2e");
        }

        return info;
    }

    private Map<String, Object> generateWorkPlanWithLlm(
            RunContext context,
            String branchName,
            TicketPlan ticketPlan,
            RepoStructure repoStructure,
            AgentsMdInfo agentsMdInfo) {
        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(ticketPlan, repoStructure, agentsMdInfo);
            Map<String, Object> schema = buildWorkPlanSchema();

            log.info("Sending request to LLM for work plan generation");
            String llmResponse = llmService.generateStructuredOutput(systemPrompt, userPrompt, schema);

            log.debug("Received LLM response: {}", llmResponse);
            Map<String, Object> workPlan = parseAndValidateLlmResponse(llmResponse);

            workPlan.put("branchName", branchName);

            enhanceWorkPlan(workPlan, agentsMdInfo, repoStructure);

            log.info("Successfully generated work plan with LLM");
            return workPlan;

        } catch (Exception e) {
            log.error("LLM generation failed, using fallback strategy: {}", e.getMessage(), e);
            return generateFallbackWorkPlan(branchName, ticketPlan, repoStructure, agentsMdInfo);
        }
    }

    private String buildSystemPrompt() {
        return """
                You are a technical qualifier analyzing a ticket plan to create a detailed work plan.
                Your job is to break down the work into concrete, actionable tasks with specific file paths and test requirements.

                Focus on:
                - Breaking down the work into logical, sequential tasks across different areas (backend, frontend, infra, docs)
                - Identifying specific file paths that will likely need to be created or modified for each task
                - Specifying appropriate test types for each task (unit tests, integration tests, E2E tests, etc.)
                - Ensuring tasks are granular enough to be independently implemented and tested
                - Considering the repository structure and existing codebase patterns

                Be specific and realistic. Tasks should be actionable and clearly scoped.
                Each task should have a clear area (backend, frontend, infra, or docs).
                File paths should be realistic based on the repository structure provided.
                """;
    }

    private String buildUserPrompt(TicketPlan ticketPlan, RepoStructure repoStructure, AgentsMdInfo agentsMdInfo) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Create a detailed work plan for the following ticket:\n\n");
        prompt.append("## Ticket Information\n");
        prompt.append("Issue #").append(ticketPlan.getIssueId()).append(": ").append(ticketPlan.getTitle())
                .append("\n\n");
        prompt.append("Summary: ").append(ticketPlan.getSummary()).append("\n\n");

        prompt.append("Acceptance Criteria:\n");
        for (String criterion : ticketPlan.getAcceptanceCriteria()) {
            prompt.append("- ").append(criterion).append("\n");
        }
        prompt.append("\n");

        if (!ticketPlan.getRisks().isEmpty()) {
            prompt.append("Risks/Considerations:\n");
            for (String risk : ticketPlan.getRisks()) {
                prompt.append("- ").append(risk).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("## Repository Structure\n");
        prompt.append("Backend files (examples): ").append(getSampleFiles(repoStructure.getBackendFiles(), 10))
                .append("\n");
        prompt.append("Frontend files (examples): ").append(getSampleFiles(repoStructure.getFrontendFiles(), 10))
                .append("\n");
        prompt.append("Test files (examples): ").append(getSampleFiles(repoStructure.getTestFiles(), 5)).append("\n");
        prompt.append("Documentation files (examples): ").append(getSampleFiles(repoStructure.getDocFiles(), 5))
                .append("\n\n");

        prompt.append("## Available Commands\n");
        prompt.append("Backend Build: ").append(agentsMdInfo.getBackendBuildCommand()).append("\n");
        prompt.append("Backend Test: ").append(agentsMdInfo.getBackendTestCommand()).append("\n");
        prompt.append("Frontend Lint: ").append(agentsMdInfo.getFrontendLintCommand()).append("\n");
        prompt.append("Frontend Test: ").append(agentsMdInfo.getFrontendTestCommand()).append("\n");
        prompt.append("E2E Test: ").append(agentsMdInfo.getE2eCommand()).append("\n\n");

        prompt.append("Based on this information, create a work plan with:\n");
        prompt.append("- At least 3 specific tasks that break down the implementation\n");
        prompt.append("- Each task should specify the area (backend, frontend, infra, or docs)\n");
        prompt.append("- Each task should list likely file paths that will be modified or created\n");
        prompt.append(
                "- Each task should specify required tests (e.g., 'Unit tests for service layer', 'Component tests', 'E2E tests')\n");
        prompt.append("- Tasks should follow a logical implementation order\n");

        return prompt.toString();
    }

    private String getSampleFiles(Set<String> files, int limit) {
        if (files.isEmpty()) {
            return "(none)";
        }

        return files.stream()
                .limit(limit)
                .collect(Collectors.joining(", "));
    }

    private Map<String, Object> buildWorkPlanSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> properties = new HashMap<>();

        properties.put("tasks", Map.of(
                "type", "array",
                "description", "List of concrete tasks to complete the ticket. Must have at least 3 tasks.",
                "minItems", 3,
                "items", Map.of(
                        "type", "object",
                        "required", List.of("id", "area", "description", "filesLikely", "tests"),
                        "additionalProperties", false,
                        "properties", Map.of(
                                "id", Map.of(
                                        "type", "string",
                                        "description", "Unique task identifier (e.g., 'task-1', 'task-2')"),
                                "area", Map.of(
                                        "type", "string",
                                        "enum", List.of("backend", "frontend", "infra", "docs"),
                                        "description", "The area of work this task belongs to"),
                                "description", Map.of(
                                        "type", "string",
                                        "description", "Clear description of what needs to be done in this task"),
                                "filesLikely", Map.of(
                                        "type", "array",
                                        "description",
                                        "List of specific file paths or directories that will likely be modified or created",
                                        "items", Map.of("type", "string")),
                                "tests", Map.of(
                                        "type", "array",
                                        "description", "List of test types or specific tests required for this task",
                                        "items", Map.of("type", "string"))))));

        properties.put("definitionOfDone", Map.of(
                "type", "array",
                "description", "List of criteria that define when all work is complete",
                "items", Map.of("type", "string")));

        schema.put("properties", properties);
        schema.put("required", List.of("tasks", "definitionOfDone"));

        return schema;
    }

    private Map<String, Object> parseAndValidateLlmResponse(String llmResponse) throws JsonProcessingException {
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("LLM response is empty");
        }

        String cleanedResponse = llmResponse.trim();
        if (cleanedResponse.startsWith("```json")) {
            cleanedResponse = cleanedResponse.substring(7);
        }
        if (cleanedResponse.startsWith("```")) {
            cleanedResponse = cleanedResponse.substring(3);
        }
        if (cleanedResponse.endsWith("```")) {
            cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
        }
        cleanedResponse = cleanedResponse.trim();

        Map<String, Object> workPlan;
        try {
            workPlan = objectMapper.readValue(cleanedResponse, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM response as JSON: {}", e.getMessage());
            throw e;
        }

        return workPlan;
    }

    private void enhanceWorkPlan(Map<String, Object> workPlan, AgentsMdInfo agentsMdInfo, RepoStructure repoStructure) {
        if (!workPlan.containsKey("commands")) {
            workPlan.put("commands", Map.of(
                    "backendVerify", agentsMdInfo.getBackendBuildCommand(),
                    "frontendLint", agentsMdInfo.getFrontendLintCommand(),
                    "frontendTest", agentsMdInfo.getFrontendTestCommand(),
                    "e2e", agentsMdInfo.getE2eCommand()));
        }

        List<Map<String, Object>> tasks = (List<Map<String, Object>>) workPlan.get("tasks");
        if (tasks != null) {
            for (Map<String, Object> task : tasks) {
                List<String> filesLikely = (List<String>) task.get("filesLikely");
                if (filesLikely == null || filesLikely.isEmpty()) {
                    String area = (String) task.get("area");
                    task.put("filesLikely", inferFilesForArea(area, repoStructure));
                }

                List<String> tests = (List<String>) task.get("tests");
                if (tests == null || tests.isEmpty()) {
                    task.put("tests", List.of("Unit tests"));
                }
            }
        }

        if (!workPlan.containsKey("definitionOfDone") ||
                ((List<?>) workPlan.get("definitionOfDone")).isEmpty()) {
            workPlan.put("definitionOfDone", List.of(
                    "All tests pass",
                    "Code follows project conventions",
                    "PR created and ready for review"));
        }
    }

    private List<String> inferFilesForArea(String area, RepoStructure repoStructure) {
        return switch (area) {
            case "backend" -> repoStructure.getBackendFiles().stream().limit(3).collect(Collectors.toList());
            case "frontend" -> repoStructure.getFrontendFiles().stream().limit(3).collect(Collectors.toList());
            case "infra" -> repoStructure.getInfraFiles().stream().limit(3).collect(Collectors.toList());
            case "docs" -> repoStructure.getDocFiles().stream().limit(3).collect(Collectors.toList());
            default -> List.of();
        };
    }

    private Map<String, Object> generateFallbackWorkPlan(
            String branchName,
            TicketPlan ticketPlan,
            RepoStructure repoStructure,
            AgentsMdInfo agentsMdInfo) {
        log.info("Generating fallback work plan");

        List<Map<String, Object>> tasks = new ArrayList<>();
        int taskCounter = 1;

        boolean hasBackendWork = ticketPlan.getSummary().toLowerCase().contains("backend") ||
                ticketPlan.getSummary().toLowerCase().contains("api") ||
                ticketPlan.getSummary().toLowerCase().contains("service");
        boolean hasFrontendWork = ticketPlan.getSummary().toLowerCase().contains("frontend") ||
                ticketPlan.getSummary().toLowerCase().contains("ui") ||
                ticketPlan.getSummary().toLowerCase().contains("interface");

        if (hasBackendWork || (!hasBackendWork && !hasFrontendWork)) {
            tasks.add(Map.of(
                    "id", "task-" + taskCounter++,
                    "area", "backend",
                    "description", "Implement backend changes for " + ticketPlan.getTitle(),
                    "filesLikely", List.of("ai-orchestrator/src/main/java/"),
                    "tests", List.of("Unit tests", "Integration tests")));
        }

        if (hasFrontendWork || (!hasBackendWork && !hasFrontendWork)) {
            tasks.add(Map.of(
                    "id", "task-" + taskCounter++,
                    "area", "frontend",
                    "description", "Implement frontend changes for " + ticketPlan.getTitle(),
                    "filesLikely", List.of("frontend/src/"),
                    "tests", List.of("Component tests", "Unit tests")));
        }

        if (hasFrontendWork && !hasBackendWork) {
            tasks.add(Map.of(
                    "id", "task-" + taskCounter++,
                    "area", "frontend",
                    "description", "Add E2E tests for " + ticketPlan.getTitle(),
                    "filesLikely", List.of("frontend/e2e/"),
                    "tests", List.of("E2E tests")));
        }

        tasks.add(Map.of(
                "id", "task-" + taskCounter++,
                "area", "backend",
                "description", "Verify and test implementation for " + ticketPlan.getTitle(),
                "filesLikely", List.of(),
                "tests", List.of("Unit tests", "Integration tests")));

        tasks.add(Map.of(
                "id", "task-" + taskCounter,
                "area", "docs",
                "description", "Update documentation for " + ticketPlan.getTitle(),
                "filesLikely", List.of("docs/", "README.md"),
                "tests", List.of()));

        return Map.of(
                "branchName", branchName,
                "tasks", tasks,
                "commands", Map.of(
                        "backendVerify", agentsMdInfo.getBackendBuildCommand(),
                        "frontendLint", agentsMdInfo.getFrontendLintCommand(),
                        "frontendTest", agentsMdInfo.getFrontendTestCommand(),
                        "e2e", agentsMdInfo.getE2eCommand()),
                "definitionOfDone", List.of(
                        "All acceptance criteria met",
                        "All tests pass",
                        "Linting clean",
                        "PR created and reviewed"));
    }

    private void validateWorkPlan(Map<String, Object> workPlan) {
        if (!workPlan.containsKey("branchName")) {
            throw new IllegalArgumentException("Work plan missing required field: branchName");
        }

        if (!workPlan.containsKey("tasks")) {
            throw new IllegalArgumentException("Work plan missing required field: tasks");
        }

        List<Map<String, Object>> tasks = (List<Map<String, Object>>) workPlan.get("tasks");
        if (tasks.size() < 3) {
            throw new IllegalArgumentException("Work plan must have at least 3 tasks, found: " + tasks.size());
        }

        for (Map<String, Object> task : tasks) {
            if (!task.containsKey("id") || !task.containsKey("area") ||
                    !task.containsKey("description") || !task.containsKey("filesLikely") ||
                    !task.containsKey("tests")) {
                throw new IllegalArgumentException("Task missing required fields: " + task);
            }

            String area = (String) task.get("area");
            if (!List.of("backend", "frontend", "infra", "docs").contains(area)) {
                throw new IllegalArgumentException("Invalid task area: " + area);
            }
        }

        if (!workPlan.containsKey("commands")) {
            throw new IllegalArgumentException("Work plan missing required field: commands");
        }

        Map<String, Object> commands = (Map<String, Object>) workPlan.get("commands");
        if (!commands.containsKey("backendVerify") || !commands.containsKey("frontendLint") ||
                !commands.containsKey("frontendTest") || !commands.containsKey("e2e")) {
            throw new IllegalArgumentException("Commands missing required fields");
        }

        if (!workPlan.containsKey("definitionOfDone")) {
            throw new IllegalArgumentException("Work plan missing required field: definitionOfDone");
        }
    }

    private static class RepoStructure {
        private final Set<String> allFiles = new HashSet<>();
        private final Set<String> backendFiles = new LinkedHashSet<>();
        private final Set<String> frontendFiles = new LinkedHashSet<>();
        private final Set<String> testFiles = new LinkedHashSet<>();
        private final Set<String> docFiles = new LinkedHashSet<>();
        private final Set<String> infraFiles = new LinkedHashSet<>();

        public void addFile(String path) {
            allFiles.add(path);
        }

        public void addBackendFile(String path) {
            backendFiles.add(path);
        }

        public void addFrontendFile(String path) {
            frontendFiles.add(path);
        }

        public void addTestFile(String path) {
            testFiles.add(path);
        }

        public void addDocFile(String path) {
            docFiles.add(path);
        }

        public void addInfraFile(String path) {
            infraFiles.add(path);
        }

        public Set<String> getAllFiles() {
            return allFiles;
        }

        public Set<String> getBackendFiles() {
            return backendFiles;
        }

        public Set<String> getFrontendFiles() {
            return frontendFiles;
        }

        public Set<String> getTestFiles() {
            return testFiles;
        }

        public Set<String> getDocFiles() {
            return docFiles;
        }

        public Set<String> getInfraFiles() {
            return infraFiles;
        }
    }

    private static class AgentsMdInfo {
        private String backendBuildCommand = "cd ai-orchestrator && mvn clean verify";
        private String backendTestCommand = "cd ai-orchestrator && mvn test";
        private String frontendLintCommand = "cd frontend && npm run lint";
        private String frontendTestCommand = "cd frontend && npm test -- --watch=false";
        private String e2eCommand = "cd frontend && npm run e2e";

        public void parseFromContent(String content) {
            String buildMatch = extractCommand(content, "Build");
            if (buildMatch != null) {
                if (buildMatch.contains("mvn")) {
                    backendBuildCommand = buildMatch;
                }
            }

            String testMatch = extractCommand(content, "Test");
            if (testMatch != null) {
                if (testMatch.contains("mvn")) {
                    backendTestCommand = testMatch;
                } else if (testMatch.contains("npm test")) {
                    frontendTestCommand = testMatch;
                }
            }

            String lintMatch = extractCommand(content, "Lint");
            if (lintMatch != null) {
                frontendLintCommand = lintMatch;
            }
        }

        private String extractCommand(String content, String label) {
            String pattern = "(?i)[-*]\\s*\\*\\*" + label + "\\*\\*:\\s*`([^`]+)`";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(content);

            if (m.find()) {
                return m.group(1);
            }

            return null;
        }

        public String getBackendBuildCommand() {
            return backendBuildCommand;
        }

        public void setBackendBuildCommand(String backendBuildCommand) {
            this.backendBuildCommand = backendBuildCommand;
        }

        public String getBackendTestCommand() {
            return backendTestCommand;
        }

        public void setBackendTestCommand(String backendTestCommand) {
            this.backendTestCommand = backendTestCommand;
        }

        public String getFrontendLintCommand() {
            return frontendLintCommand;
        }

        public void setFrontendLintCommand(String frontendLintCommand) {
            this.frontendLintCommand = frontendLintCommand;
        }

        public String getFrontendTestCommand() {
            return frontendTestCommand;
        }

        public void setFrontendTestCommand(String frontendTestCommand) {
            this.frontendTestCommand = frontendTestCommand;
        }

        public String getE2eCommand() {
            return e2eCommand;
        }

        public void setE2eCommand(String e2eCommand) {
            this.e2eCommand = e2eCommand;
        }
    }
}
