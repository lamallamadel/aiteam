package com.atlasia.ai.service;

import com.atlasia.ai.model.RunArtifactEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ArchitectStep implements AgentStep {
    private static final Logger log = LoggerFactory.getLogger(ArchitectStep.class);

    private final LlmService llmService;
    private final GitHubApiClient gitHubApiClient;
    private final ObjectMapper objectMapper;

    public ArchitectStep(LlmService llmService, GitHubApiClient gitHubApiClient, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.gitHubApiClient = gitHubApiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        log.info("Starting architecture analysis for issue #{}", context.getRunEntity().getIssueNumber());

        String repoContext = gatherRepoContext(context);
        
        ArchitectureAnalysis analysis = performLlmAnalysis(context, repoContext);
        
        storeReasoningTraceAsArtifact(context, analysis);

        String architectureNotes = generateArchitectureNotes(context, analysis);
        
        context.setArchitectureNotes(architectureNotes);
        
        log.info("Completed architecture analysis for issue #{}", context.getRunEntity().getIssueNumber());
        return architectureNotes;
    }

    private String gatherRepoContext(RunContext context) {
        log.debug("Gathering repository context");
        StringBuilder repoContext = new StringBuilder();
        
        try {
            Map<String, Object> mainRef = gitHubApiClient.getReference(
                context.getOwner(), 
                context.getRepo(), 
                "heads/main"
            );
            Map<String, Object> mainObject = (Map<String, Object>) mainRef.get("object");
            String baseSha = (String) mainObject.get("sha");

            Map<String, Object> tree = gitHubApiClient.getRepoTree(
                context.getOwner(),
                context.getRepo(),
                baseSha,
                true
            );

            List<Map<String, Object>> treeItems = (List<Map<String, Object>>) tree.get("tree");
            
            repoContext.append("## Repository Structure\n\n");
            repoContext.append("### Key Files and Directories:\n");
            
            Map<String, List<String>> categorizedFiles = categorizeFiles(treeItems);
            
            for (Map.Entry<String, List<String>> entry : categorizedFiles.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    repoContext.append("\n**").append(entry.getKey()).append(":**\n");
                    entry.getValue().stream()
                        .limit(10)
                        .forEach(file -> repoContext.append("- ").append(file).append("\n"));
                }
            }

            tryFetchKeyFiles(context, repoContext);

        } catch (Exception e) {
            log.warn("Failed to gather full repository context: {}", e.getMessage());
            repoContext.append("Repository context gathering was limited.\n");
        }
        
        return repoContext.toString();
    }

    private Map<String, List<String>> categorizeFiles(List<Map<String, Object>> treeItems) {
        Map<String, List<String>> categorized = new LinkedHashMap<>();
        categorized.put("Backend (Java)", new ArrayList<>());
        categorized.put("Frontend", new ArrayList<>());
        categorized.put("Configuration", new ArrayList<>());
        categorized.put("Documentation", new ArrayList<>());
        categorized.put("Tests", new ArrayList<>());
        categorized.put("Infrastructure", new ArrayList<>());
        
        for (Map<String, Object> item : treeItems) {
            String path = (String) item.get("path");
            String type = (String) item.get("type");
            
            if (!"blob".equals(type)) continue;
            
            if (path.endsWith(".java")) {
                categorized.get("Backend (Java)").add(path);
            } else if (path.endsWith(".ts") || path.endsWith(".js") || path.endsWith(".html") || path.endsWith(".css")) {
                categorized.get("Frontend").add(path);
            } else if (path.endsWith(".yml") || path.endsWith(".yaml") || path.endsWith(".xml") || 
                       path.endsWith(".properties") || path.equals("pom.xml") || path.equals("package.json")) {
                categorized.get("Configuration").add(path);
            } else if (path.endsWith(".md") || path.startsWith("docs/")) {
                categorized.get("Documentation").add(path);
            } else if (path.contains("test") || path.contains("Test")) {
                categorized.get("Tests").add(path);
            } else if (path.startsWith("infra/") || path.contains("docker") || path.contains("Dockerfile")) {
                categorized.get("Infrastructure").add(path);
            }
        }
        
        return categorized;
    }

    private void tryFetchKeyFiles(RunContext context, StringBuilder repoContext) {
        List<String> keyFiles = Arrays.asList(
            "AGENTS.md",
            "README.md",
            "pom.xml",
            "package.json"
        );
        
        for (String fileName : keyFiles) {
            try {
                Map<String, Object> fileContent = gitHubApiClient.getRepoContent(
                    context.getOwner(),
                    context.getRepo(),
                    fileName
                );
                
                String content = (String) fileContent.get("content");
                if (content != null) {
                    String decoded = new String(Base64.getDecoder().decode(content));
                    repoContext.append("\n### ").append(fileName).append(" (excerpt):\n```\n");
                    repoContext.append(truncateContent(decoded, 800));
                    repoContext.append("\n```\n");
                }
            } catch (Exception e) {
                log.debug("Could not fetch {}: {}", fileName, e.getMessage());
            }
        }
    }

    private String truncateContent(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "\n... (truncated)";
    }

    private ArchitectureAnalysis performLlmAnalysis(RunContext context, String repoContext) {
        try {
            String systemPrompt = buildArchitectureSystemPrompt();
            String userPrompt = buildArchitectureUserPrompt(context, repoContext);
            Map<String, Object> schema = buildArchitectureAnalysisSchema();

            log.info("Requesting LLM architecture analysis");
            String llmResponse = llmService.generateStructuredOutput(systemPrompt, userPrompt, schema);
            
            log.debug("Received architecture analysis from LLM");
            ArchitectureAnalysis analysis = objectMapper.readValue(llmResponse, ArchitectureAnalysis.class);
            
            validateAndEnhanceAnalysis(analysis);
            
            return analysis;
        } catch (Exception e) {
            log.error("LLM architecture analysis failed, using fallback: {}", e.getMessage(), e);
            return createFallbackAnalysis(context);
        }
    }

    private String buildArchitectureSystemPrompt() {
        return """
            You are a software architect analyzing a codebase to provide architectural guidance for implementing new features or fixing issues.
            
            Your responsibilities:
            - Analyze the repository structure and identify design patterns in use
            - Understand the existing architectural layers and component organization
            - Identify appropriate design patterns for the requested change
            - Create component diagrams using Mermaid syntax to visualize the architecture
            - Provide ADR (Architecture Decision Record) style justifications for design choices
            - Consider technical risks, dependencies, and integration points
            - Ensure consistency with existing codebase patterns and conventions
            
            Focus on:
            - Design patterns: Identify patterns like MVC, Repository, Service Layer, Factory, Strategy, etc.
            - Component relationships: How components interact and depend on each other
            - Data flow: How information flows through the system
            - Technology stack: Frameworks, libraries, and tools in use
            - Architectural layers: Separation of concerns (controller, service, repository, entity)
            - Testing strategy: Unit, integration, and e2e test approach
            
            Be specific, technical, and provide actionable guidance.
            """;
    }

    private String buildArchitectureUserPrompt(RunContext context, String repoContext) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("# Architecture Analysis Request\n\n");
        prompt.append("## Issue Information\n");
        prompt.append("**Issue #").append(context.getRunEntity().getIssueNumber()).append("**\n\n");
        
        if (context.getIssueData() != null) {
            String title = (String) context.getIssueData().get("title");
            String body = (String) context.getIssueData().getOrDefault("body", "");
            
            prompt.append("**Title:** ").append(title).append("\n\n");
            prompt.append("**Description:**\n").append(body != null ? body : "(no description)").append("\n\n");
        }
        
        if (context.getTicketPlan() != null && !context.getTicketPlan().isEmpty()) {
            prompt.append("## Ticket Plan\n");
            prompt.append(context.getTicketPlan()).append("\n\n");
        }

        prompt.append("## Repository Context\n");
        prompt.append(repoContext).append("\n\n");

        prompt.append("## Analysis Required\n");
        prompt.append("Please provide:\n");
        prompt.append("1. Identified design patterns currently used in the codebase\n");
        prompt.append("2. Recommended design patterns for implementing this change\n");
        prompt.append("3. Component diagram (Mermaid syntax) showing affected components\n");
        prompt.append("4. Architecture decisions with ADR-style justifications\n");
        prompt.append("5. Component integration points and data flow\n");
        prompt.append("6. Technical risks and mitigation strategies\n");
        
        return prompt.toString();
    }

    private Map<String, Object> buildArchitectureAnalysisSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        
        Map<String, Object> properties = new HashMap<>();
        
        properties.put("identifiedPatterns", Map.of(
            "type", "array",
            "description", "Design patterns identified in the existing codebase",
            "items", Map.of(
                "type", "object",
                "properties", Map.of(
                    "pattern", Map.of("type", "string", "description", "Pattern name (e.g., Repository, MVC, Factory)"),
                    "location", Map.of("type", "string", "description", "Where this pattern is used"),
                    "description", Map.of("type", "string", "description", "How the pattern is implemented")
                ),
                "required", List.of("pattern", "location", "description"),
                "additionalProperties", false
            ),
            "minItems", 1
        ));
        
        properties.put("recommendedPatterns", Map.of(
            "type", "array",
            "description", "Design patterns recommended for this implementation",
            "items", Map.of(
                "type", "object",
                "properties", Map.of(
                    "pattern", Map.of("type", "string", "description", "Pattern name"),
                    "rationale", Map.of("type", "string", "description", "Why this pattern is appropriate"),
                    "implementation", Map.of("type", "string", "description", "How to implement this pattern")
                ),
                "required", List.of("pattern", "rationale", "implementation"),
                "additionalProperties", false
            ),
            "minItems", 1
        ));
        
        properties.put("componentDiagram", Map.of(
            "type", "string",
            "description", "Mermaid diagram showing component architecture and relationships"
        ));
        
        properties.put("architectureDecisions", Map.of(
            "type", "array",
            "description", "ADR-style architecture decisions",
            "items", Map.of(
                "type", "object",
                "properties", Map.of(
                    "title", Map.of("type", "string", "description", "Decision title"),
                    "context", Map.of("type", "string", "description", "Context and problem statement"),
                    "decision", Map.of("type", "string", "description", "The decision made"),
                    "consequences", Map.of("type", "string", "description", "Expected consequences and tradeoffs")
                ),
                "required", List.of("title", "context", "decision", "consequences"),
                "additionalProperties", false
            ),
            "minItems", 1
        ));
        
        properties.put("componentsAffected", Map.of(
            "type", "array",
            "description", "List of components that will be affected by this change",
            "items", Map.of("type", "string"),
            "minItems", 1
        ));
        
        properties.put("dataFlow", Map.of(
            "type", "string",
            "description", "Description of how data flows through the affected components"
        ));
        
        properties.put("integrationPoints", Map.of(
            "type", "array",
            "description", "Key integration points and interfaces",
            "items", Map.of("type", "string")
        ));
        
        properties.put("technicalRisks", Map.of(
            "type", "array",
            "description", "Technical risks and mitigation strategies",
            "items", Map.of(
                "type", "object",
                "properties", Map.of(
                    "risk", Map.of("type", "string", "description", "Description of the risk"),
                    "mitigation", Map.of("type", "string", "description", "How to mitigate this risk")
                ),
                "required", List.of("risk", "mitigation"),
                "additionalProperties", false
            )
        ));
        
        properties.put("testingStrategy", Map.of(
            "type", "object",
            "properties", Map.of(
                "unitTests", Map.of("type", "string", "description", "Unit testing approach"),
                "integrationTests", Map.of("type", "string", "description", "Integration testing approach"),
                "e2eTests", Map.of("type", "string", "description", "End-to-end testing approach")
            ),
            "required", List.of("unitTests", "integrationTests", "e2eTests"),
            "additionalProperties", false
        ));
        
        schema.put("properties", properties);
        schema.put("required", List.of(
            "identifiedPatterns", 
            "recommendedPatterns", 
            "componentDiagram",
            "architectureDecisions",
            "componentsAffected",
            "dataFlow",
            "integrationPoints",
            "technicalRisks",
            "testingStrategy"
        ));
        
        return schema;
    }

    private void validateAndEnhanceAnalysis(ArchitectureAnalysis analysis) {
        if (analysis.getIdentifiedPatterns() == null || analysis.getIdentifiedPatterns().isEmpty()) {
            log.warn("No patterns identified by LLM, adding defaults");
            analysis.setIdentifiedPatterns(getDefaultPatterns());
        }
        
        if (analysis.getRecommendedPatterns() == null || analysis.getRecommendedPatterns().isEmpty()) {
            log.warn("No recommended patterns from LLM, adding defaults");
            analysis.setRecommendedPatterns(getDefaultRecommendedPatterns());
        }
        
        if (analysis.getComponentDiagram() == null || analysis.getComponentDiagram().isEmpty()) {
            log.warn("No component diagram from LLM, generating default");
            analysis.setComponentDiagram(generateDefaultComponentDiagram());
        }
        
        if (analysis.getArchitectureDecisions() == null || analysis.getArchitectureDecisions().isEmpty()) {
            log.warn("No architecture decisions from LLM, adding defaults");
            analysis.setArchitectureDecisions(getDefaultArchitectureDecisions());
        }
    }

    private ArchitectureAnalysis createFallbackAnalysis(RunContext context) {
        log.info("Creating fallback architecture analysis");
        
        ArchitectureAnalysis analysis = new ArchitectureAnalysis();
        analysis.setIdentifiedPatterns(getDefaultPatterns());
        analysis.setRecommendedPatterns(getDefaultRecommendedPatterns());
        analysis.setComponentDiagram(generateDefaultComponentDiagram());
        analysis.setArchitectureDecisions(getDefaultArchitectureDecisions());
        analysis.setComponentsAffected(Arrays.asList("Backend services", "Frontend components", "Database schema"));
        analysis.setDataFlow("Request flows from frontend → controller → service → repository → database");
        analysis.setIntegrationPoints(Arrays.asList("REST API endpoints", "Database transactions", "Frontend-backend communication"));
        
        List<TechnicalRisk> risks = new ArrayList<>();
        risks.add(new TechnicalRisk(
            "Implementation complexity may require careful design",
            "Break down into smaller components, use existing patterns"
        ));
        analysis.setTechnicalRisks(risks);
        
        TestingStrategy testingStrategy = new TestingStrategy(
            "Unit tests for business logic in service layer",
            "Integration tests for API endpoints and database operations",
            "E2E tests for complete user workflows"
        );
        analysis.setTestingStrategy(testingStrategy);
        
        return analysis;
    }

    private List<DesignPattern> getDefaultPatterns() {
        List<DesignPattern> patterns = new ArrayList<>();
        patterns.add(new DesignPattern(
            "Layered Architecture",
            "Backend: controller → service → repository → entity layers",
            "Spring Boot follows standard layered architecture with clear separation of concerns"
        ));
        patterns.add(new DesignPattern(
            "Repository Pattern",
            "Backend: JPA repositories in persistence package",
            "Data access abstraction using Spring Data JPA repositories"
        ));
        patterns.add(new DesignPattern(
            "Dependency Injection",
            "Throughout the application",
            "Spring Framework's constructor injection for managing dependencies"
        ));
        return patterns;
    }

    private List<RecommendedPattern> getDefaultRecommendedPatterns() {
        List<RecommendedPattern> patterns = new ArrayList<>();
        patterns.add(new RecommendedPattern(
            "Service Layer Pattern",
            "Encapsulates business logic and promotes reusability",
            "Create service classes with @Service annotation, inject repositories, implement business logic"
        ));
        patterns.add(new RecommendedPattern(
            "DTO Pattern",
            "Separates internal models from API contracts",
            "Create DTO classes for API requests/responses, use mappers to convert between DTOs and entities"
        ));
        return patterns;
    }

    private List<ArchitectureDecision> getDefaultArchitectureDecisions() {
        List<ArchitectureDecision> decisions = new ArrayList<>();
        decisions.add(new ArchitectureDecision(
            "Follow existing layered architecture",
            "The codebase uses a standard Spring Boot layered architecture (controller → service → repository → entity)",
            "Maintain consistency by implementing changes within the existing architectural layers",
            "Consistency with existing code, easier maintenance, familiar patterns for the team"
        ));
        decisions.add(new ArchitectureDecision(
            "Use Spring Data JPA for persistence",
            "Database access is managed through Spring Data JPA repositories",
            "Continue using JPA repositories for any new database entities or queries",
            "Leverages existing infrastructure, reduces boilerplate, provides transaction management"
        ));
        return decisions;
    }

    private String generateDefaultComponentDiagram() {
        return """
            graph TB
                User[User/Client]
                Controller[REST Controller]
                Service[Service Layer]
                Repository[Repository Layer]
                Database[(Database)]
                
                User -->|HTTP Request| Controller
                Controller -->|Call| Service
                Service -->|Data Access| Repository
                Repository -->|SQL| Database
                Database -->|Results| Repository
                Repository -->|Entities| Service
                Service -->|Response| Controller
                Controller -->|HTTP Response| User
                
                style Controller fill:#e1f5ff
                style Service fill:#fff4e1
                style Repository fill:#e1ffe1
                style Database fill:#ffe1e1
            """;
    }

    private void storeReasoningTraceAsArtifact(RunContext context, ArchitectureAnalysis analysis) {
        try {
            Map<String, Object> reasoningTrace = new LinkedHashMap<>();
            reasoningTrace.put("timestamp", Instant.now().toString());
            reasoningTrace.put("issueNumber", context.getRunEntity().getIssueNumber());
            reasoningTrace.put("analysisType", "architecture_design_patterns");
            reasoningTrace.put("identifiedPatterns", analysis.getIdentifiedPatterns());
            reasoningTrace.put("recommendedPatterns", analysis.getRecommendedPatterns());
            reasoningTrace.put("architectureDecisions", analysis.getArchitectureDecisions());
            reasoningTrace.put("technicalRisks", analysis.getTechnicalRisks());
            reasoningTrace.put("componentsAffected", analysis.getComponentsAffected());
            reasoningTrace.put("dataFlow", analysis.getDataFlow());
            reasoningTrace.put("integrationPoints", analysis.getIntegrationPoints());
            
            String payload = objectMapper.writeValueAsString(reasoningTrace);
            
            RunArtifactEntity artifact = new RunArtifactEntity(
                "architect",
                "reasoning_trace",
                payload,
                Instant.now()
            );
            
            context.getRunEntity().addArtifact(artifact);
            
            log.info("Stored reasoning trace as artifact for issue #{}", context.getRunEntity().getIssueNumber());
        } catch (Exception e) {
            log.error("Failed to store reasoning trace artifact: {}", e.getMessage(), e);
        }
    }

    private String generateArchitectureNotes(RunContext context, ArchitectureAnalysis analysis) {
        StringBuilder notes = new StringBuilder();
        
        notes.append("# Architecture Notes\n\n");
        notes.append("**Issue:** #").append(context.getRunEntity().getIssueNumber()).append("\n\n");
        
        notes.append("## Overview\n");
        notes.append("This document outlines the architectural approach for implementing the requested changes, ");
        notes.append("including design pattern analysis, component architecture, and technical decisions.\n\n");
        
        notes.append("## Identified Design Patterns\n\n");
        notes.append("The following design patterns were identified in the existing codebase:\n\n");
        for (DesignPattern pattern : analysis.getIdentifiedPatterns()) {
            notes.append("### ").append(pattern.getPattern()).append("\n");
            notes.append("- **Location:** ").append(pattern.getLocation()).append("\n");
            notes.append("- **Implementation:** ").append(pattern.getDescription()).append("\n\n");
        }
        
        notes.append("## Recommended Design Patterns\n\n");
        notes.append("For this implementation, the following design patterns are recommended:\n\n");
        for (RecommendedPattern pattern : analysis.getRecommendedPatterns()) {
            notes.append("### ").append(pattern.getPattern()).append("\n");
            notes.append("- **Rationale:** ").append(pattern.getRationale()).append("\n");
            notes.append("- **Implementation Approach:** ").append(pattern.getImplementation()).append("\n\n");
        }
        
        notes.append("## Component Architecture\n\n");
        notes.append("### Component Diagram\n\n");
        notes.append("```mermaid\n");
        notes.append(analysis.getComponentDiagram());
        notes.append("\n```\n\n");
        
        notes.append("### Components Affected\n\n");
        for (String component : analysis.getComponentsAffected()) {
            notes.append("- ").append(component).append("\n");
        }
        notes.append("\n");
        
        notes.append("### Data Flow\n\n");
        notes.append(analysis.getDataFlow()).append("\n\n");
        
        notes.append("### Integration Points\n\n");
        for (String integrationPoint : analysis.getIntegrationPoints()) {
            notes.append("- ").append(integrationPoint).append("\n");
        }
        notes.append("\n");
        
        notes.append("## Architecture Decision Records (ADRs)\n\n");
        for (ArchitectureDecision decision : analysis.getArchitectureDecisions()) {
            notes.append("### ADR: ").append(decision.getTitle()).append("\n\n");
            notes.append("**Context:**\n").append(decision.getContext()).append("\n\n");
            notes.append("**Decision:**\n").append(decision.getDecision()).append("\n\n");
            notes.append("**Consequences:**\n").append(decision.getConsequences()).append("\n\n");
        }
        
        notes.append("## Technical Risks & Mitigations\n\n");
        if (analysis.getTechnicalRisks() != null && !analysis.getTechnicalRisks().isEmpty()) {
            for (TechnicalRisk risk : analysis.getTechnicalRisks()) {
                notes.append("**Risk:** ").append(risk.getRisk()).append("\n");
                notes.append("**Mitigation:** ").append(risk.getMitigation()).append("\n\n");
            }
        } else {
            notes.append("No significant technical risks identified.\n\n");
        }
        
        notes.append("## Testing Strategy\n\n");
        TestingStrategy testing = analysis.getTestingStrategy();
        notes.append("### Unit Tests\n");
        notes.append(testing.getUnitTests()).append("\n\n");
        notes.append("### Integration Tests\n");
        notes.append(testing.getIntegrationTests()).append("\n\n");
        notes.append("### End-to-End Tests\n");
        notes.append(testing.getE2eTests()).append("\n\n");
        
        notes.append("## Implementation Guidelines\n\n");
        notes.append("- Follow the existing layered architecture pattern\n");
        notes.append("- Maintain separation of concerns across layers\n");
        notes.append("- Use dependency injection for all component dependencies\n");
        notes.append("- Ensure proper error handling and logging\n");
        notes.append("- Write comprehensive tests at all levels\n");
        notes.append("- Document public APIs and complex logic\n");
        
        return notes.toString();
    }

    public static class ArchitectureAnalysis {
        private List<DesignPattern> identifiedPatterns;
        private List<RecommendedPattern> recommendedPatterns;
        private String componentDiagram;
        private List<ArchitectureDecision> architectureDecisions;
        private List<String> componentsAffected;
        private String dataFlow;
        private List<String> integrationPoints;
        private List<TechnicalRisk> technicalRisks;
        private TestingStrategy testingStrategy;

        public List<DesignPattern> getIdentifiedPatterns() { return identifiedPatterns; }
        public void setIdentifiedPatterns(List<DesignPattern> identifiedPatterns) { this.identifiedPatterns = identifiedPatterns; }
        
        public List<RecommendedPattern> getRecommendedPatterns() { return recommendedPatterns; }
        public void setRecommendedPatterns(List<RecommendedPattern> recommendedPatterns) { this.recommendedPatterns = recommendedPatterns; }
        
        public String getComponentDiagram() { return componentDiagram; }
        public void setComponentDiagram(String componentDiagram) { this.componentDiagram = componentDiagram; }
        
        public List<ArchitectureDecision> getArchitectureDecisions() { return architectureDecisions; }
        public void setArchitectureDecisions(List<ArchitectureDecision> architectureDecisions) { this.architectureDecisions = architectureDecisions; }
        
        public List<String> getComponentsAffected() { return componentsAffected; }
        public void setComponentsAffected(List<String> componentsAffected) { this.componentsAffected = componentsAffected; }
        
        public String getDataFlow() { return dataFlow; }
        public void setDataFlow(String dataFlow) { this.dataFlow = dataFlow; }
        
        public List<String> getIntegrationPoints() { return integrationPoints; }
        public void setIntegrationPoints(List<String> integrationPoints) { this.integrationPoints = integrationPoints; }
        
        public List<TechnicalRisk> getTechnicalRisks() { return technicalRisks; }
        public void setTechnicalRisks(List<TechnicalRisk> technicalRisks) { this.technicalRisks = technicalRisks; }
        
        public TestingStrategy getTestingStrategy() { return testingStrategy; }
        public void setTestingStrategy(TestingStrategy testingStrategy) { this.testingStrategy = testingStrategy; }
    }

    public static class DesignPattern {
        private String pattern;
        private String location;
        private String description;

        public DesignPattern() {}
        
        public DesignPattern(String pattern, String location, String description) {
            this.pattern = pattern;
            this.location = location;
            this.description = description;
        }

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class RecommendedPattern {
        private String pattern;
        private String rationale;
        private String implementation;

        public RecommendedPattern() {}
        
        public RecommendedPattern(String pattern, String rationale, String implementation) {
            this.pattern = pattern;
            this.rationale = rationale;
            this.implementation = implementation;
        }

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        
        public String getRationale() { return rationale; }
        public void setRationale(String rationale) { this.rationale = rationale; }
        
        public String getImplementation() { return implementation; }
        public void setImplementation(String implementation) { this.implementation = implementation; }
    }

    public static class ArchitectureDecision {
        private String title;
        private String context;
        private String decision;
        private String consequences;

        public ArchitectureDecision() {}
        
        public ArchitectureDecision(String title, String context, String decision, String consequences) {
            this.title = title;
            this.context = context;
            this.decision = decision;
            this.consequences = consequences;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        
        public String getConsequences() { return consequences; }
        public void setConsequences(String consequences) { this.consequences = consequences; }
    }

    public static class TechnicalRisk {
        private String risk;
        private String mitigation;

        public TechnicalRisk() {}
        
        public TechnicalRisk(String risk, String mitigation) {
            this.risk = risk;
            this.mitigation = mitigation;
        }

        public String getRisk() { return risk; }
        public void setRisk(String risk) { this.risk = risk; }
        
        public String getMitigation() { return mitigation; }
        public void setMitigation(String mitigation) { this.mitigation = mitigation; }
    }

    public static class TestingStrategy {
        private String unitTests;
        private String integrationTests;
        private String e2eTests;

        public TestingStrategy() {}
        
        public TestingStrategy(String unitTests, String integrationTests, String e2eTests) {
            this.unitTests = unitTests;
            this.integrationTests = integrationTests;
            this.e2eTests = e2eTests;
        }

        public String getUnitTests() { return unitTests; }
        public void setUnitTests(String unitTests) { this.unitTests = unitTests; }
        
        public String getIntegrationTests() { return integrationTests; }
        public void setIntegrationTests(String integrationTests) { this.integrationTests = integrationTests; }
        
        public String getE2eTests() { return e2eTests; }
        public void setE2eTests(String e2eTests) { this.e2eTests = e2eTests; }
    }
}
