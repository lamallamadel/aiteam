package com.atlasia.ai.service;

import com.atlasia.ai.model.RunArtifactEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class WriterStep implements AgentStep {
    private static final Logger log = LoggerFactory.getLogger(WriterStep.class);
    
    private final GitHubApiClient gitHubApiClient;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public WriterStep(GitHubApiClient gitHubApiClient, LlmService llmService, ObjectMapper objectMapper) {
        this.gitHubApiClient = gitHubApiClient;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        log.info("Starting documentation generation for issue #{}", context.getRunEntity().getIssueNumber());
        
        String owner = context.getOwner();
        String repo = context.getRepo();
        String branchName = context.getBranchName();

        try {
            DocumentationGaps gaps = detectDocumentationGaps(context);
            
            CompletableFuture<String> changelogFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return generateComprehensiveChangelog(context, gaps);
                } catch (Exception e) {
                    log.error("Failed to generate changelog: {}", e.getMessage(), e);
                    return generateFallbackChangelog(context);
                }
            });
            
            CompletableFuture<Map<String, String>> readmeUpdatesFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return generateReadmeUpdates(context, gaps);
                } catch (Exception e) {
                    log.error("Failed to generate README updates: {}", e.getMessage(), e);
                    return Collections.emptyMap();
                }
            });
            
            CompletableFuture<Map<String, String>> codeDocsFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return generateInlineCodeDocumentation(context, gaps);
                } catch (Exception e) {
                    log.error("Failed to generate code documentation: {}", e.getMessage(), e);
                    return Collections.emptyMap();
                }
            });

            String changelog = changelogFuture.get(30, TimeUnit.SECONDS);
            Map<String, String> readmeUpdates = readmeUpdatesFuture.get(30, TimeUnit.SECONDS);
            Map<String, String> codeDocs = codeDocsFuture.get(30, TimeUnit.SECONDS);

            validateMarkdownFormatting(changelog);
            for (String content : readmeUpdates.values()) {
                validateMarkdownFormatting(content);
            }
            for (String content : codeDocs.values()) {
                validateMarkdownFormatting(content);
            }

            applyDocumentationChanges(context, owner, repo, branchName, changelog, readmeUpdates, codeDocs);

            storeDocumentationArtifact(context, changelog, readmeUpdates, codeDocs, gaps);

            log.info("Documentation generation completed for issue #{}", context.getRunEntity().getIssueNumber());
            return "Documentation updated for PR: " + context.getPrUrl();
            
        } catch (Exception e) {
            log.error("Failed to execute writer step: {}", e.getMessage(), e);
            throw new WriterStepException("Documentation generation failed: " + e.getMessage(), e);
        }
    }

    private DocumentationGaps detectDocumentationGaps(RunContext context) {
        log.debug("Detecting documentation gaps from work plan and context");
        
        DocumentationGaps gaps = new DocumentationGaps();
        
        gaps.setNeedsChangelog(true);
        
        String workPlan = context.getWorkPlan();
        if (workPlan != null && !workPlan.isEmpty()) {
            try {
                Map<String, Object> planMap = objectMapper.readValue(workPlan, Map.class);
                
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) planMap.get("tasks");
                if (tasks != null) {
                    for (Map<String, Object> task : tasks) {
                        String area = (String) task.get("area");
                        String description = (String) task.get("description");
                        
                        if ("docs".equals(area)) {
                            gaps.setNeedsReadmeUpdate(true);
                        }
                        
                        if (description != null) {
                            if (description.toLowerCase().contains("readme") || 
                                description.toLowerCase().contains("documentation")) {
                                gaps.setNeedsReadmeUpdate(true);
                            }
                            
                            if (description.toLowerCase().contains("api") || 
                                description.toLowerCase().contains("endpoint") ||
                                description.toLowerCase().contains("service")) {
                                gaps.setNeedsApiDocumentation(true);
                            }
                        }
                        
                        List<String> filesLikely = (List<String>) task.get("filesLikely");
                        if (filesLikely != null) {
                            gaps.addAffectedFiles(filesLikely);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse work plan for gap detection: {}", e.getMessage());
            }
        }
        
        String architectureNotes = context.getArchitectureNotes();
        if (architectureNotes != null && !architectureNotes.isEmpty()) {
            if (architectureNotes.toLowerCase().contains("new component") ||
                architectureNotes.toLowerCase().contains("new pattern")) {
                gaps.setNeedsArchitectureDoc(true);
            }
        }
        
        if (context.getIssueData() != null) {
            String body = (String) context.getIssueData().getOrDefault("body", "");
            if (body != null) {
                if (body.toLowerCase().contains("breaking change")) {
                    gaps.setBreakingChange(true);
                }
            }
        }
        
        log.info("Documentation gaps detected - README: {}, API: {}, Architecture: {}, Breaking: {}", 
                gaps.isNeedsReadmeUpdate(), gaps.isNeedsApiDocumentation(), 
                gaps.isNeedsArchitectureDoc(), gaps.isBreakingChange());
        
        return gaps;
    }

    private String generateComprehensiveChangelog(RunContext context, DocumentationGaps gaps) {
        log.info("Generating comprehensive changelog with LLM");
        
        try {
            String systemPrompt = buildChangelogSystemPrompt();
            String userPrompt = buildChangelogUserPrompt(context, gaps);
            Map<String, Object> schema = buildChangelogSchema();

            String llmResponse = llmService.generateStructuredOutput(systemPrompt, userPrompt, schema);
            
            ChangelogContent changelogContent = objectMapper.readValue(llmResponse, ChangelogContent.class);
            
            return formatChangelog(context, changelogContent);
        } catch (Exception e) {
            log.error("LLM changelog generation failed: {}", e.getMessage(), e);
            return generateFallbackChangelog(context);
        }
    }

    private String buildChangelogSystemPrompt() {
        return """
            You are a technical writer creating comprehensive changelogs for software releases.
            
            Your responsibilities:
            - Write clear, concise changelog entries following Keep a Changelog format
            - Categorize changes appropriately (Added, Changed, Deprecated, Removed, Fixed, Security)
            - Include technical details while remaining accessible
            - Highlight breaking changes prominently
            - Document migration steps when needed
            - Reference related issues and pull requests
            - Use proper Markdown formatting
            
            Follow these principles:
            - Write from the user's perspective
            - Be specific and actionable
            - Use past tense for completed work
            - Include code examples for complex changes
            - Link to relevant documentation
            """;
    }

    private String buildChangelogUserPrompt(RunContext context, DocumentationGaps gaps) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("# Changelog Generation Request\n\n");
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
        
        if (context.getWorkPlan() != null && !context.getWorkPlan().isEmpty()) {
            prompt.append("## Work Plan\n");
            prompt.append(context.getWorkPlan()).append("\n\n");
        }
        
        if (context.getArchitectureNotes() != null && !context.getArchitectureNotes().isEmpty()) {
            prompt.append("## Architecture Context\n");
            String architectureExcerpt = truncateContent(context.getArchitectureNotes(), 1500);
            prompt.append(architectureExcerpt).append("\n\n");
        }
        
        prompt.append("## Documentation Requirements\n");
        prompt.append("- Breaking change: ").append(gaps.isBreakingChange()).append("\n");
        prompt.append("- Files affected: ").append(gaps.getAffectedFiles().size()).append("\n\n");
        
        prompt.append("Please generate a comprehensive changelog entry with:\n");
        prompt.append("1. A clear summary of what changed and why\n");
        prompt.append("2. Categorized changes (Added, Changed, Fixed, etc.)\n");
        prompt.append("3. Technical details and implementation notes\n");
        prompt.append("4. Breaking changes section if applicable\n");
        prompt.append("5. Migration guide if needed\n");
        prompt.append("6. Related links and references\n");
        
        return prompt.toString();
    }

    private Map<String, Object> buildChangelogSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        
        Map<String, Object> properties = new HashMap<>();
        
        properties.put("summary", Map.of(
            "type", "string",
            "description", "Brief summary of the change (2-3 sentences)"
        ));
        
        properties.put("added", Map.of(
            "type", "array",
            "description", "New features or capabilities added",
            "items", Map.of("type", "string")
        ));
        
        properties.put("changed", Map.of(
            "type", "array",
            "description", "Changes to existing functionality",
            "items", Map.of("type", "string")
        ));
        
        properties.put("deprecated", Map.of(
            "type", "array",
            "description", "Features that are deprecated but still available",
            "items", Map.of("type", "string")
        ));
        
        properties.put("removed", Map.of(
            "type", "array",
            "description", "Features or code that was removed",
            "items", Map.of("type", "string")
        ));
        
        properties.put("fixed", Map.of(
            "type", "array",
            "description", "Bug fixes",
            "items", Map.of("type", "string")
        ));
        
        properties.put("security", Map.of(
            "type", "array",
            "description", "Security-related changes",
            "items", Map.of("type", "string")
        ));
        
        properties.put("technicalDetails", Map.of(
            "type", "string",
            "description", "Technical implementation details and considerations"
        ));
        
        properties.put("breakingChanges", Map.of(
            "type", "array",
            "description", "Breaking changes with details",
            "items", Map.of("type", "string")
        ));
        
        properties.put("migrationGuide", Map.of(
            "type", "string",
            "description", "Migration guide for breaking changes (if applicable)"
        ));
        
        schema.put("properties", properties);
        schema.put("required", List.of("summary", "added", "changed", "deprecated", 
                                       "removed", "fixed", "security", "technicalDetails", 
                                       "breakingChanges", "migrationGuide"));
        
        return schema;
    }

    private String formatChangelog(RunContext context, ChangelogContent content) {
        StringBuilder changelog = new StringBuilder();
        
        changelog.append("# Changelog\n\n");
        changelog.append("## Issue #").append(context.getRunEntity().getIssueNumber());
        
        if (context.getIssueData() != null) {
            String title = (String) context.getIssueData().get("title");
            if (title != null) {
                changelog.append(" - ").append(title);
            }
        }
        changelog.append("\n\n");
        
        changelog.append("### Summary\n\n");
        changelog.append(content.getSummary()).append("\n\n");
        
        if (!content.getBreakingChanges().isEmpty()) {
            changelog.append("### BREAKING CHANGES\n\n");
            for (String change : content.getBreakingChanges()) {
                changelog.append("- ").append(change).append("\n");
            }
            changelog.append("\n");
            
            if (content.getMigrationGuide() != null && !content.getMigrationGuide().isEmpty()) {
                changelog.append("#### Migration Guide\n\n");
                changelog.append(content.getMigrationGuide()).append("\n\n");
            }
        }
        
        if (!content.getAdded().isEmpty()) {
            changelog.append("### Added\n\n");
            for (String item : content.getAdded()) {
                changelog.append("- ").append(item).append("\n");
            }
            changelog.append("\n");
        }
        
        if (!content.getChanged().isEmpty()) {
            changelog.append("### Changed\n\n");
            for (String item : content.getChanged()) {
                changelog.append("- ").append(item).append("\n");
            }
            changelog.append("\n");
        }
        
        if (!content.getFixed().isEmpty()) {
            changelog.append("### Fixed\n\n");
            for (String item : content.getFixed()) {
                changelog.append("- ").append(item).append("\n");
            }
            changelog.append("\n");
        }
        
        if (!content.getDeprecated().isEmpty()) {
            changelog.append("### Deprecated\n\n");
            for (String item : content.getDeprecated()) {
                changelog.append("- ").append(item).append("\n");
            }
            changelog.append("\n");
        }
        
        if (!content.getRemoved().isEmpty()) {
            changelog.append("### Removed\n\n");
            for (String item : content.getRemoved()) {
                changelog.append("- ").append(item).append("\n");
            }
            changelog.append("\n");
        }
        
        if (!content.getSecurity().isEmpty()) {
            changelog.append("### Security\n\n");
            for (String item : content.getSecurity()) {
                changelog.append("- ").append(item).append("\n");
            }
            changelog.append("\n");
        }
        
        changelog.append("### Technical Details\n\n");
        changelog.append(content.getTechnicalDetails()).append("\n\n");
        
        changelog.append("### References\n\n");
        changelog.append("- Issue: #").append(context.getRunEntity().getIssueNumber()).append("\n");
        if (context.getPrUrl() != null) {
            changelog.append("- Pull Request: ").append(context.getPrUrl()).append("\n");
        }
        
        return changelog.toString();
    }

    private String generateFallbackChangelog(RunContext context) {
        StringBuilder changelog = new StringBuilder();
        changelog.append("# Changelog\n\n");
        changelog.append("## Issue #").append(context.getRunEntity().getIssueNumber()).append("\n\n");
        
        if (context.getIssueData() != null) {
            String title = (String) context.getIssueData().get("title");
            changelog.append("**").append(title).append("**\n\n");
        }
        
        changelog.append("### Changes\n");
        changelog.append("- Implemented requested functionality\n");
        changelog.append("- Added tests\n");
        changelog.append("- Updated documentation\n\n");
        
        changelog.append("### References\n");
        changelog.append("- Issue: #").append(context.getRunEntity().getIssueNumber()).append("\n");
        if (context.getPrUrl() != null) {
            changelog.append("- Pull Request: ").append(context.getPrUrl()).append("\n");
        }
        
        return changelog.toString();
    }

    private Map<String, String> generateReadmeUpdates(RunContext context, DocumentationGaps gaps) {
        if (!gaps.isNeedsReadmeUpdate()) {
            log.debug("No README updates needed");
            return Collections.emptyMap();
        }
        
        log.info("Generating README updates with LLM");
        
        try {
            String owner = context.getOwner();
            String repo = context.getRepo();
            
            Map<String, Object> readmeContent = gitHubApiClient.getRepoContent(owner, repo, "README.md");
            String contentBase64 = (String) readmeContent.get("content");
            String currentReadme = new String(Base64.getDecoder().decode(contentBase64.replaceAll("\\s", "")));
            
            String systemPrompt = buildReadmeSystemPrompt();
            String userPrompt = buildReadmeUserPrompt(context, currentReadme, gaps);
            Map<String, Object> schema = buildReadmeUpdateSchema();

            String llmResponse = llmService.generateStructuredOutput(systemPrompt, userPrompt, schema);
            
            ReadmeUpdate update = objectMapper.readValue(llmResponse, ReadmeUpdate.class);
            
            Map<String, String> updates = new HashMap<>();
            if (update.getShouldUpdate()) {
                String updatedReadme = applyReadmeUpdates(currentReadme, update);
                updates.put("README.md", updatedReadme);
            }
            
            return updates;
        } catch (Exception e) {
            log.error("Failed to generate README updates: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private String buildReadmeSystemPrompt() {
        return """
            You are a technical writer updating README documentation.
            
            Your responsibilities:
            - Identify which README sections need updates based on the changes
            - Write clear, concise documentation improvements
            - Maintain the existing README structure and style
            - Add new sections only when truly necessary
            - Update existing content rather than duplicating
            - Ensure all code examples are correct and tested
            - Keep documentation up-to-date with implementation
            
            Follow these principles:
            - Be concise and to the point
            - Use proper Markdown formatting
            - Include code examples where helpful
            - Maintain consistent tone with existing documentation
            """;
    }

    private String buildReadmeUserPrompt(RunContext context, String currentReadme, DocumentationGaps gaps) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("# README Update Request\n\n");
        prompt.append("## Current README\n\n```markdown\n");
        prompt.append(truncateContent(currentReadme, 2000));
        prompt.append("\n```\n\n");
        
        prompt.append("## Changes Made\n");
        prompt.append("Issue #").append(context.getRunEntity().getIssueNumber()).append("\n\n");
        
        if (context.getIssueData() != null) {
            String title = (String) context.getIssueData().get("title");
            prompt.append("**").append(title).append("**\n\n");
        }
        
        if (context.getTicketPlan() != null) {
            prompt.append(truncateContent(context.getTicketPlan(), 800)).append("\n\n");
        }
        
        prompt.append("## Analysis\n");
        prompt.append("Determine if the README needs updates and suggest specific changes.\n");
        prompt.append("Consider:\n");
        prompt.append("- New features that should be documented\n");
        prompt.append("- Changed APIs or configurations\n");
        prompt.append("- New setup or installation steps\n");
        prompt.append("- Updated usage examples\n");
        prompt.append("- Breaking changes requiring migration notes\n");
        
        return prompt.toString();
    }

    private Map<String, Object> buildReadmeUpdateSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        
        Map<String, Object> properties = new HashMap<>();
        
        properties.put("shouldUpdate", Map.of(
            "type", "boolean",
            "description", "Whether the README needs updating"
        ));
        
        properties.put("sectionsToUpdate", Map.of(
            "type", "array",
            "description", "List of README sections that need updates",
            "items", Map.of(
                "type", "object",
                "properties", Map.of(
                    "sectionName", Map.of("type", "string", "description", "Name of the section to update"),
                    "updateType", Map.of("type", "string", "enum", List.of("add", "modify", "remove"), "description", "Type of update"),
                    "newContent", Map.of("type", "string", "description", "New or updated content for this section"),
                    "reason", Map.of("type", "string", "description", "Why this section needs updating")
                ),
                "required", List.of("sectionName", "updateType", "newContent", "reason"),
                "additionalProperties", false
            )
        ));
        
        schema.put("properties", properties);
        schema.put("required", List.of("shouldUpdate", "sectionsToUpdate"));
        
        return schema;
    }

    private String applyReadmeUpdates(String currentReadme, ReadmeUpdate update) {
        String updatedReadme = currentReadme;
        
        for (SectionUpdate section : update.getSectionsToUpdate()) {
            Pattern sectionPattern = Pattern.compile(
                "(?m)^#+\\s+" + Pattern.quote(section.getSectionName()) + "\\s*$.*?(?=^#+\\s+|\\z)",
                Pattern.DOTALL
            );
            
            Matcher matcher = sectionPattern.matcher(updatedReadme);
            
            if ("modify".equals(section.getUpdateType()) && matcher.find()) {
                updatedReadme = matcher.replaceFirst("## " + section.getSectionName() + "\n\n" + section.getNewContent() + "\n\n");
            } else if ("add".equals(section.getUpdateType())) {
                updatedReadme = updatedReadme + "\n\n## " + section.getSectionName() + "\n\n" + section.getNewContent() + "\n";
            } else if ("remove".equals(section.getUpdateType()) && matcher.find()) {
                updatedReadme = matcher.replaceFirst("");
            }
        }
        
        return updatedReadme;
    }

    private Map<String, String> generateInlineCodeDocumentation(RunContext context, DocumentationGaps gaps) {
        if (!gaps.isNeedsApiDocumentation()) {
            log.debug("No inline code documentation needed");
            return Collections.emptyMap();
        }
        
        log.info("Generating inline code documentation suggestions");
        
        Map<String, String> docs = new HashMap<>();
        
        if (gaps.getAffectedFiles().isEmpty()) {
            return docs;
        }
        
        List<String> javaFiles = gaps.getAffectedFiles().stream()
            .filter(f -> f.endsWith(".java"))
            .limit(5)
            .collect(Collectors.toList());
        
        if (javaFiles.isEmpty()) {
            return docs;
        }
        
        try {
            String systemPrompt = buildCodeDocSystemPrompt();
            String userPrompt = buildCodeDocUserPrompt(context, javaFiles);
            
            String llmResponse = llmService.generateCompletion(systemPrompt, userPrompt);
            
            docs.put("docs/CODE_DOCUMENTATION_GUIDE.md", llmResponse);
            
        } catch (Exception e) {
            log.error("Failed to generate code documentation: {}", e.getMessage(), e);
        }
        
        return docs;
    }

    private String buildCodeDocSystemPrompt() {
        return """
            You are a technical writer creating inline code documentation guides.
            
            Provide specific recommendations for:
            - Javadoc for public APIs, classes, and methods
            - Inline comments for complex logic
            - README sections for API documentation
            - Example usage code
            
            Be specific and actionable. Focus on what needs documentation and why.
            """;
    }

    private String buildCodeDocUserPrompt(RunContext context, List<String> affectedFiles) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("# Code Documentation Guide Request\n\n");
        prompt.append("Issue #").append(context.getRunEntity().getIssueNumber()).append("\n\n");
        
        prompt.append("## Files Affected\n");
        for (String file : affectedFiles) {
            prompt.append("- ").append(file).append("\n");
        }
        prompt.append("\n");
        
        if (context.getArchitectureNotes() != null) {
            prompt.append("## Architecture Context\n");
            prompt.append(truncateContent(context.getArchitectureNotes(), 1000)).append("\n\n");
        }
        
        prompt.append("Provide a documentation guide that includes:\n");
        prompt.append("1. Which classes/methods need Javadoc and what should be documented\n");
        prompt.append("2. Complex logic that needs inline comments\n");
        prompt.append("3. API usage examples\n");
        prompt.append("4. Integration points that need documentation\n");
        
        return prompt.toString();
    }

    private void validateMarkdownFormatting(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return;
        }
        
        List<String> errors = new ArrayList<>();
        
        Pattern unclosedCodeBlock = Pattern.compile("```(?:[^`]|`[^`]|``[^`])*$", Pattern.DOTALL);
        if (unclosedCodeBlock.matcher(markdown).find()) {
            errors.add("Unclosed code block detected");
        }
        
        Pattern headingPattern = Pattern.compile("^#{1,6}\\s+.+$", Pattern.MULTILINE);
        Matcher headingMatcher = headingPattern.matcher(markdown);
        while (headingMatcher.find()) {
            String heading = headingMatcher.group();
            if (heading.endsWith("#")) {
                errors.add("Heading has trailing # characters: " + heading);
            }
        }
        
        Pattern linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
        Matcher linkMatcher = linkPattern.matcher(markdown);
        while (linkMatcher.find()) {
            String url = linkMatcher.group(2);
            if (url.trim().isEmpty()) {
                errors.add("Empty link URL detected");
            }
        }
        
        Pattern listPattern = Pattern.compile("^([-*+])\\s", Pattern.MULTILINE);
        Matcher listMatcher = listPattern.matcher(markdown);
        String lastBullet = null;
        while (listMatcher.find()) {
            String bullet = listMatcher.group(1);
            if (lastBullet != null && !lastBullet.equals(bullet)) {
                log.warn("Inconsistent list bullet styles detected");
            }
            lastBullet = bullet;
        }
        
        if (!errors.isEmpty()) {
            log.warn("Markdown validation warnings: {}", String.join(", ", errors));
        }
    }

    private void applyDocumentationChanges(RunContext context, String owner, String repo, 
                                          String branchName, String changelog, 
                                          Map<String, String> readmeUpdates,
                                          Map<String, String> codeDocs) throws Exception {
        log.info("Applying documentation changes to branch {}", branchName);
        
        Map<String, Object> branchRef = gitHubApiClient.getReference(owner, repo, "heads/" + branchName);
        Map<String, Object> branchObject = (Map<String, Object>) branchRef.get("object");
        String currentSha = (String) branchObject.get("sha");
        
        List<Map<String, Object>> treeEntries = new ArrayList<>();
        
        String changelogPath = "docs/CHANGELOG_" + context.getRunEntity().getIssueNumber() + ".md";
        String changelogEncoded = Base64.getEncoder().encodeToString(changelog.getBytes());
        Map<String, Object> changelogBlob = gitHubApiClient.createBlob(owner, repo, changelogEncoded, "base64");
        treeEntries.add(Map.of(
            "path", changelogPath,
            "mode", "100644",
            "type", "blob",
            "sha", changelogBlob.get("sha")
        ));
        log.debug("Added changelog to tree: {}", changelogPath);
        
        for (Map.Entry<String, String> entry : readmeUpdates.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            
            try {
                Map<String, Object> existingFile = gitHubApiClient.getRepoContent(owner, repo, path);
                String existingSha = (String) existingFile.get("sha");
                
                String encoded = Base64.getEncoder().encodeToString(content.getBytes());
                Map<String, Object> blob = gitHubApiClient.createBlob(owner, repo, encoded, "base64");
                treeEntries.add(Map.of(
                    "path", path,
                    "mode", "100644",
                    "type", "blob",
                    "sha", blob.get("sha")
                ));
                log.debug("Updated README in tree: {}", path);
            } catch (Exception e) {
                log.warn("Failed to update {}: {}", path, e.getMessage());
            }
        }
        
        for (Map.Entry<String, String> entry : codeDocs.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            
            try {
                String encoded = Base64.getEncoder().encodeToString(content.getBytes());
                Map<String, Object> blob = gitHubApiClient.createBlob(owner, repo, encoded, "base64");
                treeEntries.add(Map.of(
                    "path", path,
                    "mode", "100644",
                    "type", "blob",
                    "sha", blob.get("sha")
                ));
                log.debug("Added code documentation to tree: {}", path);
            } catch (Exception e) {
                log.warn("Failed to add code doc {}: {}", path, e.getMessage());
            }
        }
        
        if (treeEntries.isEmpty()) {
            log.warn("No documentation changes to apply");
            return;
        }
        
        Map<String, Object> newTree = gitHubApiClient.createTree(owner, repo, treeEntries, currentSha);
        String treeSha = (String) newTree.get("sha");
        
        String commitMessage = "docs: Update documentation for issue #" + context.getRunEntity().getIssueNumber() + 
                              "\n\n- Add comprehensive changelog\n" +
                              (readmeUpdates.isEmpty() ? "" : "- Update README\n") +
                              (codeDocs.isEmpty() ? "" : "- Add code documentation guide\n");
        
        Map<String, Object> author = Map.of(
            "name", "Atlasia AI Bot",
            "email", "ai-bot@atlasia.io",
            "date", Instant.now().toString()
        );
        
        Map<String, Object> newCommit = gitHubApiClient.createCommit(
            owner, repo, commitMessage, treeSha, List.of(currentSha), author, author
        );
        String commitSha = (String) newCommit.get("sha");
        
        gitHubApiClient.updateReference(owner, repo, "heads/" + branchName, commitSha, false);
        
        log.info("Documentation changes committed: {}", commitSha);
    }

    private void storeDocumentationArtifact(RunContext context, String changelog, 
                                           Map<String, String> readmeUpdates,
                                           Map<String, String> codeDocs,
                                           DocumentationGaps gaps) {
        try {
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("timestamp", Instant.now().toString());
            artifact.put("issueNumber", context.getRunEntity().getIssueNumber());
            artifact.put("artifactType", "documentation");
            artifact.put("changelogLength", changelog.length());
            artifact.put("readmeUpdateCount", readmeUpdates.size());
            artifact.put("codeDocCount", codeDocs.size());
            artifact.put("gaps", Map.of(
                "needsReadmeUpdate", gaps.isNeedsReadmeUpdate(),
                "needsApiDocumentation", gaps.isNeedsApiDocumentation(),
                "needsArchitectureDoc", gaps.isNeedsArchitectureDoc(),
                "breakingChange", gaps.isBreakingChange(),
                "affectedFilesCount", gaps.getAffectedFiles().size()
            ));
            
            String payload = objectMapper.writeValueAsString(artifact);
            
            RunArtifactEntity artifactEntity = new RunArtifactEntity(
                "writer",
                "documentation",
                payload,
                Instant.now()
            );
            
            context.getRunEntity().addArtifact(artifactEntity);
            
            log.info("Stored documentation artifact for issue #{}", context.getRunEntity().getIssueNumber());
        } catch (Exception e) {
            log.error("Failed to store documentation artifact: {}", e.getMessage(), e);
        }
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "\n... (truncated)";
    }

    public static class DocumentationGaps {
        private boolean needsChangelog = false;
        private boolean needsReadmeUpdate = false;
        private boolean needsApiDocumentation = false;
        private boolean needsArchitectureDoc = false;
        private boolean breakingChange = false;
        private Set<String> affectedFiles = new HashSet<>();

        public boolean isNeedsChangelog() { return needsChangelog; }
        public void setNeedsChangelog(boolean needsChangelog) { this.needsChangelog = needsChangelog; }
        
        public boolean isNeedsReadmeUpdate() { return needsReadmeUpdate; }
        public void setNeedsReadmeUpdate(boolean needsReadmeUpdate) { this.needsReadmeUpdate = needsReadmeUpdate; }
        
        public boolean isNeedsApiDocumentation() { return needsApiDocumentation; }
        public void setNeedsApiDocumentation(boolean needsApiDocumentation) { this.needsApiDocumentation = needsApiDocumentation; }
        
        public boolean isNeedsArchitectureDoc() { return needsArchitectureDoc; }
        public void setNeedsArchitectureDoc(boolean needsArchitectureDoc) { this.needsArchitectureDoc = needsArchitectureDoc; }
        
        public boolean isBreakingChange() { return breakingChange; }
        public void setBreakingChange(boolean breakingChange) { this.breakingChange = breakingChange; }
        
        public Set<String> getAffectedFiles() { return affectedFiles; }
        public void addAffectedFiles(List<String> files) { this.affectedFiles.addAll(files); }
    }

    public static class ChangelogContent {
        private String summary;
        private List<String> added = new ArrayList<>();
        private List<String> changed = new ArrayList<>();
        private List<String> deprecated = new ArrayList<>();
        private List<String> removed = new ArrayList<>();
        private List<String> fixed = new ArrayList<>();
        private List<String> security = new ArrayList<>();
        private String technicalDetails;
        private List<String> breakingChanges = new ArrayList<>();
        private String migrationGuide;

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        
        public List<String> getAdded() { return added; }
        public void setAdded(List<String> added) { this.added = added; }
        
        public List<String> getChanged() { return changed; }
        public void setChanged(List<String> changed) { this.changed = changed; }
        
        public List<String> getDeprecated() { return deprecated; }
        public void setDeprecated(List<String> deprecated) { this.deprecated = deprecated; }
        
        public List<String> getRemoved() { return removed; }
        public void setRemoved(List<String> removed) { this.removed = removed; }
        
        public List<String> getFixed() { return fixed; }
        public void setFixed(List<String> fixed) { this.fixed = fixed; }
        
        public List<String> getSecurity() { return security; }
        public void setSecurity(List<String> security) { this.security = security; }
        
        public String getTechnicalDetails() { return technicalDetails; }
        public void setTechnicalDetails(String technicalDetails) { this.technicalDetails = technicalDetails; }
        
        public List<String> getBreakingChanges() { return breakingChanges; }
        public void setBreakingChanges(List<String> breakingChanges) { this.breakingChanges = breakingChanges; }
        
        public String getMigrationGuide() { return migrationGuide; }
        public void setMigrationGuide(String migrationGuide) { this.migrationGuide = migrationGuide; }
    }

    public static class ReadmeUpdate {
        private Boolean shouldUpdate;
        private List<SectionUpdate> sectionsToUpdate = new ArrayList<>();

        public Boolean getShouldUpdate() { return shouldUpdate; }
        public void setShouldUpdate(Boolean shouldUpdate) { this.shouldUpdate = shouldUpdate; }
        
        public List<SectionUpdate> getSectionsToUpdate() { return sectionsToUpdate; }
        public void setSectionsToUpdate(List<SectionUpdate> sectionsToUpdate) { this.sectionsToUpdate = sectionsToUpdate; }
    }

    public static class SectionUpdate {
        private String sectionName;
        private String updateType;
        private String newContent;
        private String reason;

        public String getSectionName() { return sectionName; }
        public void setSectionName(String sectionName) { this.sectionName = sectionName; }
        
        public String getUpdateType() { return updateType; }
        public void setUpdateType(String updateType) { this.updateType = updateType; }
        
        public String getNewContent() { return newContent; }
        public void setNewContent(String newContent) { this.newContent = newContent; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class WriterStepException extends RuntimeException {
        public WriterStepException(String message) {
            super(message);
        }
        
        public WriterStepException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
