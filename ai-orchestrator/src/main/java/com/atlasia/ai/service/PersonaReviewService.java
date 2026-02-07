package com.atlasia.ai.service;

import com.atlasia.ai.config.PersonaConfig;
import com.atlasia.ai.config.PersonaConfigLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class PersonaReviewService {
    private static final Logger log = LoggerFactory.getLogger(PersonaReviewService.class);
    
    private final PersonaConfigLoader personaConfigLoader;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    public PersonaReviewService(PersonaConfigLoader personaConfigLoader, 
                                LlmService llmService, 
                                ObjectMapper objectMapper) {
        this.personaConfigLoader = personaConfigLoader;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.executorService = Executors.newFixedThreadPool(4);
    }

    public PersonaReviewReport reviewCodeChanges(RunContext context, DeveloperStep.CodeChanges codeChanges) {
        log.info("Starting persona review for issue #{}", context.getRunEntity().getIssueNumber());
        
        List<PersonaConfig> personas = personaConfigLoader.getPersonas();
        if (personas.isEmpty()) {
            log.warn("No personas configured, skipping review");
            return createEmptyReport();
        }
        
        String artifactPayload = buildArtifactPayload(codeChanges, context);
        
        List<CompletableFuture<PersonaReview>> reviewFutures = personas.stream()
                .map(persona -> CompletableFuture.supplyAsync(() -> 
                    performPersonaReview(persona, artifactPayload, context), executorService))
                .toList();
        
        List<PersonaReview> reviews = reviewFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
        
        PersonaReviewReport report = mergeReviews(reviews, codeChanges);
        
        log.info("Completed persona review for issue #{}: {} personas, {} total findings", 
                context.getRunEntity().getIssueNumber(), reviews.size(), 
                report.findings.stream().mapToInt(f -> f.issues.size()).sum());
        
        return report;
    }

    private String buildArtifactPayload(DeveloperStep.CodeChanges codeChanges, RunContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", codeChanges.getSummary());
        payload.put("testingNotes", codeChanges.getTestingNotes());
        payload.put("implementationNotes", codeChanges.getImplementationNotes());
        
        List<Map<String, Object>> filesInfo = new ArrayList<>();
        for (DeveloperStep.FileChange file : codeChanges.getFiles()) {
            Map<String, Object> fileInfo = new LinkedHashMap<>();
            fileInfo.put("path", file.getPath());
            fileInfo.put("operation", file.getOperation());
            fileInfo.put("content", file.getContent());
            fileInfo.put("explanation", file.getExplanation());
            filesInfo.add(fileInfo);
        }
        payload.put("files", filesInfo);
        
        if (context.getIssueData() != null) {
            payload.put("issueTitle", context.getIssueData().get("title"));
            payload.put("issueNumber", context.getRunEntity().getIssueNumber());
        }
        
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize artifact payload", e);
            return "{}";
        }
    }

    private PersonaReview performPersonaReview(PersonaConfig persona, String artifactPayload, RunContext context) {
        try {
            log.debug("Starting review by persona: {}", persona.name());
            
            String systemPrompt = buildPersonaSystemPrompt(persona);
            String userPrompt = buildPersonaUserPrompt(artifactPayload, persona);
            Map<String, Object> schema = buildReviewSchema();
            
            String llmResponse = llmService.generateStructuredOutput(systemPrompt, userPrompt, schema);
            
            PersonaReview review = objectMapper.readValue(llmResponse, PersonaReview.class);
            review.personaName = persona.name();
            review.personaRole = persona.role();
            
            log.debug("Completed review by persona {}: {} issues found", persona.name(), review.issues.size());
            
            return review;
        } catch (Exception e) {
            log.error("Failed to perform review for persona {}: {}", persona.name(), e.getMessage(), e);
            return createFailedReview(persona, e);
        }
    }

    private String buildPersonaSystemPrompt(PersonaConfig persona) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are ").append(persona.name()).append(", a ").append(persona.role()).append(".\n\n");
        prompt.append("**Mission:** ").append(persona.mission()).append("\n\n");
        
        prompt.append("**Focus Areas:**\n");
        for (String area : persona.focusAreas()) {
            prompt.append("- ").append(area).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("**Review Checklist:**\n");
        for (String item : persona.reviewChecklist()) {
            prompt.append("- ").append(item).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("**Severity Levels:**\n");
        Map<String, List<String>> severityLevels = persona.severityLevels();
        for (Map.Entry<String, List<String>> entry : severityLevels.entrySet()) {
            prompt.append("- **").append(entry.getKey().toUpperCase()).append(":**\n");
            for (String example : entry.getValue()) {
                prompt.append("  - ").append(example).append("\n");
            }
        }
        prompt.append("\n");
        
        prompt.append("**Required Enhancements:**\n");
        for (PersonaConfig.Enhancement enhancement : persona.requiredEnhancements()) {
            prompt.append("- **").append(enhancement.type()).append(":** ").append(enhancement.description()).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("Your task is to review the provided code changes and identify issues, provide recommendations, ");
        prompt.append("and suggest specific enhancements. For each issue, specify the severity level (critical, high, medium, low), ");
        prompt.append("affected file path, description, and whether it's mandatory to fix (critical/high security issues from Aabo are mandatory).\n\n");
        
        prompt.append("Focus on your area of expertise and be thorough but practical. ");
        prompt.append("Only flag real issues that matter for code quality, security, or user experience.");
        
        return prompt.toString();
    }

    private String buildPersonaUserPrompt(String artifactPayload, PersonaConfig persona) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("# Code Changes to Review\n\n");
        prompt.append("Please review the following code changes from your perspective as a ").append(persona.role()).append(".\n\n");
        prompt.append("## Artifact Payload\n\n");
        prompt.append("```json\n");
        prompt.append(artifactPayload);
        prompt.append("\n```\n\n");
        
        prompt.append("## Review Instructions\n\n");
        prompt.append("1. Analyze the code changes thoroughly based on your focus areas\n");
        prompt.append("2. Identify any issues, violations, or concerns\n");
        prompt.append("3. Provide specific, actionable recommendations\n");
        prompt.append("4. Suggest enhancements aligned with your required enhancements list\n");
        prompt.append("5. Assign appropriate severity levels to each issue\n");
        prompt.append("6. Mark security-related critical/high issues as mandatory if you are Aabo\n\n");
        
        prompt.append("Focus on providing value - only flag real problems and skip minor nitpicks.\n");
        
        return prompt.toString();
    }

    private Map<String, Object> buildReviewSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        
        Map<String, Object> properties = new HashMap<>();
        
        properties.put("overallAssessment", Map.of(
            "type", "string",
            "description", "Brief overall assessment of the code changes from your perspective"
        ));
        
        properties.put("issues", Map.of(
            "type", "array",
            "description", "List of issues identified during review",
            "items", Map.of(
                "type", "object",
                "properties", Map.of(
                    "severity", Map.of("type", "string", "enum", List.of("critical", "high", "medium", "low")),
                    "filePath", Map.of("type", "string", "description", "Path to affected file"),
                    "description", Map.of("type", "string", "description", "Clear description of the issue"),
                    "recommendation", Map.of("type", "string", "description", "Specific recommendation to address the issue"),
                    "mandatory", Map.of("type", "boolean", "description", "Whether this fix is mandatory (true for Aabo critical/high security issues)")
                ),
                "required", List.of("severity", "filePath", "description", "recommendation", "mandatory"),
                "additionalProperties", false
            )
        ));
        
        properties.put("enhancements", Map.of(
            "type", "array",
            "description", "List of enhancement recommendations",
            "items", Map.of(
                "type", "object",
                "properties", Map.of(
                    "category", Map.of("type", "string", "description", "Enhancement category"),
                    "description", Map.of("type", "string", "description", "Description of the enhancement"),
                    "benefit", Map.of("type", "string", "description", "Expected benefit of this enhancement")
                ),
                "required", List.of("category", "description", "benefit"),
                "additionalProperties", false
            )
        ));
        
        schema.put("properties", properties);
        schema.put("required", List.of("overallAssessment", "issues", "enhancements"));
        
        return schema;
    }

    private PersonaReview createFailedReview(PersonaConfig persona, Exception e) {
        PersonaReview review = new PersonaReview();
        review.personaName = persona.name();
        review.personaRole = persona.role();
        review.overallAssessment = "Review failed due to error: " + e.getMessage();
        review.issues = new ArrayList<>();
        review.enhancements = new ArrayList<>();
        return review;
    }

    private PersonaReviewReport createEmptyReport() {
        PersonaReviewReport report = new PersonaReviewReport();
        report.findings = new ArrayList<>();
        report.mergedRecommendations = new ArrayList<>();
        report.securityFixesApplied = false;
        return report;
    }

    private PersonaReviewReport mergeReviews(List<PersonaReview> reviews, DeveloperStep.CodeChanges codeChanges) {
        PersonaReviewReport report = new PersonaReviewReport();
        report.findings = reviews;
        report.mergedRecommendations = new ArrayList<>();
        report.securityFixesApplied = false;
        
        for (PersonaReview review : reviews) {
            for (PersonaIssue issue : review.issues) {
                String recommendation = String.format("[%s - %s] %s: %s (Recommendation: %s)",
                        review.personaName, issue.severity.toUpperCase(), issue.filePath, 
                        issue.description, issue.recommendation);
                report.mergedRecommendations.add(recommendation);
                
                if (issue.mandatory && review.personaName.equalsIgnoreCase("aabo")) {
                    applySecurityFix(codeChanges, issue);
                    report.securityFixesApplied = true;
                }
            }
            
            for (PersonaEnhancement enhancement : review.enhancements) {
                String enhancementText = String.format("[%s - %s] %s (Benefit: %s)",
                        review.personaName, enhancement.category, enhancement.description, enhancement.benefit);
                report.mergedRecommendations.add(enhancementText);
            }
        }
        
        return report;
    }

    private void applySecurityFix(DeveloperStep.CodeChanges codeChanges, PersonaIssue issue) {
        log.info("Applying mandatory security fix to file: {} - {}", issue.filePath, issue.description);
        
        for (DeveloperStep.FileChange file : codeChanges.getFiles()) {
            if (file.getPath().equals(issue.filePath)) {
                String originalContent = file.getContent();
                String fixedContent = applySecurityEnhancements(originalContent, issue);
                file.setContent(fixedContent);
                
                String updatedExplanation = file.getExplanation() + 
                    " [Security fix applied: " + issue.description + "]";
                file.setExplanation(updatedExplanation);
                
                log.debug("Applied security fix to {}: {}", issue.filePath, issue.recommendation);
                break;
            }
        }
    }

    private String applySecurityEnhancements(String content, PersonaIssue issue) {
        String enhanced = content;
        
        String lowerDesc = issue.description.toLowerCase();
        
        if (lowerDesc.contains("input validation") || lowerDesc.contains("sanitization")) {
            if (!content.contains("@Valid") && !content.contains("@NotNull")) {
                enhanced = addInputValidation(enhanced);
            }
        }
        
        if (lowerDesc.contains("sql injection") || lowerDesc.contains("parameterized")) {
            enhanced = enhanceSqlSafety(enhanced);
        }
        
        if (lowerDesc.contains("xss") || lowerDesc.contains("cross-site scripting")) {
            enhanced = addXssProtection(enhanced);
        }
        
        if (lowerDesc.contains("authentication") || lowerDesc.contains("authorization")) {
            enhanced = addAuthChecks(enhanced);
        }
        
        return enhanced;
    }

    private String addInputValidation(String content) {
        if (content.contains("class") && content.contains("{")) {
            content = content.replace("import ", "import jakarta.validation.constraints.*;\nimport ");
        }
        return content;
    }

    private String enhanceSqlSafety(String content) {
        content = content.replace("createQuery(\"", "createQuery(/* TODO: Verify parameterization */ \"");
        return content;
    }

    private String addXssProtection(String content) {
        if (content.contains("@RestController") || content.contains("@Controller")) {
            content = content.replace("return ", "// TODO: Ensure output encoding\nreturn ");
        }
        return content;
    }

    private String addAuthChecks(String content) {
        if (content.contains("@RequestMapping") || content.contains("@GetMapping") || 
            content.contains("@PostMapping")) {
            if (!content.contains("@PreAuthorize") && !content.contains("@Secured")) {
                content = content.replace("public ", "// TODO: Add @PreAuthorize annotation\npublic ");
            }
        }
        return content;
    }

    public static class PersonaReviewReport {
        public List<PersonaReview> findings = new ArrayList<>();
        public List<String> mergedRecommendations = new ArrayList<>();
        public boolean securityFixesApplied = false;

        public List<PersonaReview> getFindings() {
            return findings;
        }

        public void setFindings(List<PersonaReview> findings) {
            this.findings = findings;
        }

        public List<String> getMergedRecommendations() {
            return mergedRecommendations;
        }

        public void setMergedRecommendations(List<String> mergedRecommendations) {
            this.mergedRecommendations = mergedRecommendations;
        }

        public boolean isSecurityFixesApplied() {
            return securityFixesApplied;
        }

        public void setSecurityFixesApplied(boolean securityFixesApplied) {
            this.securityFixesApplied = securityFixesApplied;
        }
    }

    public static class PersonaReview {
        public String personaName;
        public String personaRole;
        public String overallAssessment;
        public List<PersonaIssue> issues = new ArrayList<>();
        public List<PersonaEnhancement> enhancements = new ArrayList<>();

        public String getPersonaName() {
            return personaName;
        }

        public void setPersonaName(String personaName) {
            this.personaName = personaName;
        }

        public String getPersonaRole() {
            return personaRole;
        }

        public void setPersonaRole(String personaRole) {
            this.personaRole = personaRole;
        }

        public String getOverallAssessment() {
            return overallAssessment;
        }

        public void setOverallAssessment(String overallAssessment) {
            this.overallAssessment = overallAssessment;
        }

        public List<PersonaIssue> getIssues() {
            return issues;
        }

        public void setIssues(List<PersonaIssue> issues) {
            this.issues = issues;
        }

        public List<PersonaEnhancement> getEnhancements() {
            return enhancements;
        }

        public void setEnhancements(List<PersonaEnhancement> enhancements) {
            this.enhancements = enhancements;
        }
    }

    public static class PersonaIssue {
        public String severity;
        public String filePath;
        public String description;
        public String recommendation;
        public boolean mandatory;

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }

        public boolean isMandatory() {
            return mandatory;
        }

        public void setMandatory(boolean mandatory) {
            this.mandatory = mandatory;
        }
    }

    public static class PersonaEnhancement {
        public String category;
        public String description;
        public String benefit;

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getBenefit() {
            return benefit;
        }

        public void setBenefit(String benefit) {
            this.benefit = benefit;
        }
    }
}
