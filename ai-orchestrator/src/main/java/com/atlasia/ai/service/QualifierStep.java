package com.atlasia.ai.service;

import com.atlasia.ai.model.TicketPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class QualifierStep implements AgentStep {
    private static final Logger log = LoggerFactory.getLogger(QualifierStep.class);

    private final ObjectMapper objectMapper;
    private final LlmService llmService;
    private final LlmComplexityResolver complexityResolver;
    private final GitHubApiClient gitHubApiClient;
    private final AgentContractLoader agentContractLoader;
    private final JsonSchemaValidator jsonSchemaValidator;

    public QualifierStep(
            ObjectMapper objectMapper,
            LlmService llmService,
            LlmComplexityResolver complexityResolver,
            GitHubApiClient gitHubApiClient,
            AgentContractLoader agentContractLoader,
            JsonSchemaValidator jsonSchemaValidator) {
        this.objectMapper = objectMapper;
        this.llmService = llmService;
        this.complexityResolver = complexityResolver;
        this.gitHubApiClient = gitHubApiClient;
        this.agentContractLoader = agentContractLoader;
        this.jsonSchemaValidator = jsonSchemaValidator;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        Integer issueNum = context.getRunEntity().getIssueNumber();
        String branchName = issueNum != null
                ? "ai/issue-" + issueNum
                : "ai/goal-" + context.getRunEntity().getId().toString().substring(0, 8);
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

        String workPlanJson = objectMapper.writeValueAsString(workPlan);
        jsonSchemaValidator.validate(workPlanJson, "work_plan.schema.json");
        return workPlanJson;
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
            Map<String, Object> schema = loadWorkPlanSchemaForLlm();

            log.info("Sending request to LLM for work plan generation");
            LlmResult llmResult = llmService.generateStructuredOutput(
                    systemPrompt, userPrompt, schema, complexityResolver.forAgent("qualifier"));
            String llmResponse = llmResult.content();

            log.debug("Received LLM response: source={}, preview={}", llmResult.source(), llmResponse.length() > 200 ? llmResponse.substring(0, 200) : llmResponse);
            Map<String, Object> workPlan = parseAndValidateLlmResponse(llmResponse);

            if (llmResult.isMock() || !workPlan.containsKey("tasks")) {
                log.warn("LLM work plan unusable (source={}, hasTasks={}), using fallback from ticket plan",
                        llmResult.source(), workPlan.containsKey("tasks"));
                return generateFallbackWorkPlan(branchName, ticketPlan, repoStructure, agentsMdInfo);
            }

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
        String yamlPrefix = agentContractLoader.systemPromptPrefix("qualifier");
        return yamlPrefix
                + """
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

    private Map<String, Object> loadWorkPlanSchemaForLlm() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/ai/schemas/work_plan.schema.json")) {
            if (is == null) {
                throw new IOException("Classpath resource not found: /ai/schemas/work_plan.schema.json");
            }
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
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
        log.info("Generating fallback work plan from PM ticket plan: title='{}', criteria={}, risks={}",
                ticketPlan.getTitle(),
                ticketPlan.getAcceptanceCriteria() != null ? ticketPlan.getAcceptanceCriteria().size() : 0,
                ticketPlan.getRisks() != null ? ticketPlan.getRisks().size() : 0);

        List<Map<String, Object>> tasks = new ArrayList<>();
        int taskCounter = 1;

        String summary = ticketPlan.getSummary() != null ? ticketPlan.getSummary().toLowerCase() : "";
        boolean hasBackend = summary.contains("backend") || summary.contains("api") ||
                summary.contains("service") || summary.contains("endpoint") || summary.contains("controller");
        boolean hasFrontend = summary.contains("frontend") || summary.contains("ui") ||
                summary.contains("component") || summary.contains("interface") || summary.contains("angular");
        boolean hasInfra = summary.contains("docker") || summary.contains("infra") ||
                summary.contains("deploy") || summary.contains("ci") || summary.contains("pipeline");

        // Derive implementation tasks from PM's acceptance criteria
        List<String> criteria = ticketPlan.getAcceptanceCriteria();
        if (criteria != null && !criteria.isEmpty()) {
            for (String criterion : criteria) {
                String area = inferAreaFromText(criterion, hasBackend, hasFrontend, hasInfra);
                List<String> files = inferFilesForArea(area, repoStructure);
                tasks.add(new HashMap<>(Map.of(
                        "id", "task-" + taskCounter++,
                        "area", area,
                        "description", criterion,
                        "filesLikely", files.isEmpty() ? inferDefaultFilesForArea(area) : files,
                        "tests", inferTestsForArea(area))));
            }
        }

        // Ensure at least 3 tasks if criteria didn't provide enough
        if (!tasks.isEmpty() && tasks.size() < 3) {
            String area = (hasBackend || (!hasBackend && !hasFrontend)) ? "backend" : "frontend";
            tasks.add(new HashMap<>(Map.of(
                    "id", "task-" + taskCounter++,
                    "area", area,
                    "description", "Write unit and integration tests for " + ticketPlan.getTitle(),
                    "filesLikely", inferDefaultFilesForArea(area),
                    "tests", List.of("Unit tests", "Integration tests"))));
        }

        // If no criteria at all, generate tasks from title/summary
        if (tasks.isEmpty()) {
            if (hasBackend || (!hasBackend && !hasFrontend)) {
                tasks.add(new HashMap<>(Map.of(
                        "id", "task-" + taskCounter++,
                        "area", "backend",
                        "description", "Implement backend logic for: " + ticketPlan.getTitle(),
                        "filesLikely", inferFilesForArea("backend", repoStructure).isEmpty()
                                ? inferDefaultFilesForArea("backend")
                                : inferFilesForArea("backend", repoStructure),
                        "tests", List.of("Unit tests", "Integration tests"))));
            }
            if (hasFrontend || (!hasBackend && !hasFrontend)) {
                tasks.add(new HashMap<>(Map.of(
                        "id", "task-" + taskCounter++,
                        "area", "frontend",
                        "description", "Implement UI changes for: " + ticketPlan.getTitle(),
                        "filesLikely", inferFilesForArea("frontend", repoStructure).isEmpty()
                                ? inferDefaultFilesForArea("frontend")
                                : inferFilesForArea("frontend", repoStructure),
                        "tests", List.of("Component tests", "Unit tests"))));
            }
            if (hasInfra) {
                tasks.add(new HashMap<>(Map.of(
                        "id", "task-" + taskCounter++,
                        "area", "infra",
                        "description", "Update infrastructure configuration for: " + ticketPlan.getTitle(),
                        "filesLikely", inferDefaultFilesForArea("infra"),
                        "tests", List.of("Integration tests"))));
            }
            tasks.add(new HashMap<>(Map.of(
                    "id", "task-" + taskCounter++,
                    "area", "backend",
                    "description", "Write tests to verify: " + ticketPlan.getTitle(),
                    "filesLikely", List.of(),
                    "tests", List.of("Unit tests", "Integration tests"))));
        }

        tasks.add(new HashMap<>(Map.of(
                "id", "task-" + taskCounter,
                "area", "docs",
                "description", "Update documentation for: " + ticketPlan.getTitle(),
                "filesLikely", List.of("docs/", "README.md"),
                "tests", List.of())));

        List<String> definitionOfDone = new ArrayList<>();
        if (criteria != null && !criteria.isEmpty()) {
            definitionOfDone.addAll(criteria);
        } else {
            definitionOfDone.add("All acceptance criteria met");
        }
        definitionOfDone.add("All tests pass");
        definitionOfDone.add("Linting clean");
        definitionOfDone.add("PR created and reviewed");

        Map<String, Object> plan = new HashMap<>();
        plan.put("branchName", branchName);
        plan.put("tasks", tasks);
        plan.put("commands", Map.of(
                "backendVerify", agentsMdInfo.getBackendBuildCommand(),
                "frontendLint", agentsMdInfo.getFrontendLintCommand(),
                "frontendTest", agentsMdInfo.getFrontendTestCommand(),
                "e2e", agentsMdInfo.getE2eCommand()));
        plan.put("definitionOfDone", definitionOfDone);
        return plan;
    }

    private String inferAreaFromText(String text, boolean defaultBackend, boolean defaultFrontend, boolean defaultInfra) {
        String lower = text.toLowerCase();
        if (lower.contains("frontend") || lower.contains("ui") || lower.contains("component") ||
                lower.contains("angular") || lower.contains("button") || lower.contains("page") ||
                lower.contains("form") || lower.contains("display") || lower.contains("render")) {
            return "frontend";
        }
        if (lower.contains("docker") || lower.contains("deploy") || lower.contains("ci") ||
                lower.contains("pipeline") || lower.contains("infra") || lower.contains("compose")) {
            return "infra";
        }
        if (lower.contains("doc") || lower.contains("readme") || lower.contains("changelog")) {
            return "docs";
        }
        if (defaultFrontend && !defaultBackend) return "frontend";
        if (defaultInfra && !defaultBackend && !defaultFrontend) return "infra";
        return "backend";
    }

    private List<String> inferDefaultFilesForArea(String area) {
        return switch (area) {
            case "frontend" -> List.of("frontend/src/app/");
            case "infra" -> List.of("infra/");
            case "docs" -> List.of("docs/", "README.md");
            default -> List.of("ai-orchestrator/src/main/java/");
        };
    }

    private List<String> inferTestsForArea(String area) {
        return switch (area) {
            case "frontend" -> List.of("Component tests", "Unit tests");
            case "infra" -> List.of("Integration tests");
            case "docs" -> List.of();
            default -> List.of("Unit tests", "Integration tests");
        };
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
