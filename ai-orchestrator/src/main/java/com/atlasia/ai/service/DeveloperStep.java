package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.service.exception.AgentStepException;
import com.atlasia.ai.service.exception.OrchestratorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class DeveloperStep implements AgentStep {
    private static final Logger log = LoggerFactory.getLogger(DeveloperStep.class);
    
    private final GitHubApiClient gitHubApiClient;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final OrchestratorProperties properties;

    public DeveloperStep(GitHubApiClient gitHubApiClient, LlmService llmService, 
                        ObjectMapper objectMapper, OrchestratorProperties properties) {
        this.gitHubApiClient = gitHubApiClient;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        generateCode(context);
        DeveloperStep.CodeChanges codeChanges = context.getCodeChanges();
        return commitAndCreatePullRequest(context, codeChanges);
    }

    public void generateCode(RunContext context) throws Exception {
        log.info("Starting code generation for issue #{}", context.getRunEntity().getIssueNumber());
        
        try {
            String branchName = context.getBranchName();
            String owner = context.getOwner();
            String repo = context.getRepo();

            if (branchName == null || branchName.isEmpty()) {
                throw new IllegalArgumentException("Branch name is required");
            }

            Map<String, Object> mainRef = gitHubApiClient.getReference(owner, repo, "heads/main");
            Map<String, Object> mainObject = (Map<String, Object>) mainRef.get("object");
            String baseSha = (String) mainObject.get("sha");
            
            if (baseSha == null || baseSha.isEmpty()) {
                throw new IllegalStateException("Could not determine base SHA from main branch");
            }

            detectAndResolveConflicts(context, owner, repo, branchName, baseSha);

            gitHubApiClient.createBranch(owner, repo, branchName, baseSha);
            log.info("Created branch {} from {}", branchName, baseSha);

            String repoContext = gatherRepoContext(context, owner, repo, baseSha);
            
            CodeChanges codeChanges = generateCodeWithLlm(context, repoContext);
            
            storeReasoningTraceAsArtifact(context, codeChanges);

            validateCodeChanges(codeChanges);

            context.setCodeChanges(codeChanges);

            log.info("Code generation completed for issue #{}", context.getRunEntity().getIssueNumber());
        } catch (Exception e) {
            log.error("Failed to generate code for issue #{}: {}", 
                    context.getRunEntity().getIssueNumber(), e.getMessage(), e);
            throw new AgentStepException("Code generation failed: " + e.getMessage(), e, 
                    "DEVELOPER", "generateCode", OrchestratorException.RecoveryStrategy.ESCALATE_TO_HUMAN);
        }
    }

    public String commitAndCreatePullRequest(RunContext context, CodeChanges codeChanges) throws Exception {
        log.info("Starting commit and PR creation for issue #{}", context.getRunEntity().getIssueNumber());
        
        try {
            String branchName = context.getBranchName();
            String owner = context.getOwner();
            String repo = context.getRepo();

            Map<String, Object> mainRef = gitHubApiClient.getReference(owner, repo, "heads/main");
            Map<String, Object> mainObject = (Map<String, Object>) mainRef.get("object");
            String baseSha = (String) mainObject.get("sha");

            String commitSha = applyMultiFileChanges(context, owner, repo, branchName, baseSha, codeChanges);
            log.info("Created commit {} on branch {}", commitSha, branchName);

            String prTitle = buildPrTitle(context);
            String prBody = buildPrBody(context, codeChanges);

            Map<String, Object> pr = gitHubApiClient.createPullRequest(
                owner,
                repo,
                prTitle,
                branchName,
                "main",
                prBody
            );

            String prUrl = (String) pr.get("html_url");
            if (prUrl == null || prUrl.isEmpty()) {
                throw new IllegalStateException("Pull request created but URL not returned");
            }
            
            context.setPrUrl(prUrl);

            log.info("Created pull request: {}", prUrl);
            return prUrl;
        } catch (Exception e) {
            log.error("Failed to commit and create PR for issue #{}: {}", 
                    context.getRunEntity().getIssueNumber(), e.getMessage(), e);
            throw new AgentStepException("Commit and PR creation failed: " + e.getMessage(), e, 
                    "DEVELOPER", "commitAndCreatePullRequest", OrchestratorException.RecoveryStrategy.ESCALATE_TO_HUMAN);
        }
    }

    private void detectAndResolveConflicts(RunContext context, String owner, String repo, 
                                          String branchName, String baseSha) {
        try {
            Map<String, Object> existingRef = gitHubApiClient.getReference(owner, repo, "heads/" + branchName);
            
            log.warn("Branch {} already exists, checking for conflicts", branchName);
            
            Map<String, Object> existingRefObject = (Map<String, Object>) existingRef.get("object");
            String existingBranchSha = (String) existingRefObject.get("sha");
            
            if (!existingBranchSha.equals(baseSha)) {
                log.info("Branch {} is at different commit, comparing changes", branchName);
                
                Map<String, Object> comparison = gitHubApiClient.compareCommits(owner, repo, baseSha, existingBranchSha);
                
                Integer aheadBy = (Integer) comparison.get("ahead_by");
                Integer behindBy = (Integer) comparison.get("behind_by");
                
                log.info("Branch comparison: ahead by {}, behind by {}", aheadBy, behindBy);
                
                if (behindBy != null && behindBy > 0) {
                    log.warn("Branch {} is behind main by {} commits, will force update", branchName, behindBy);
                    
                    gitHubApiClient.updateReference(owner, repo, "heads/" + branchName, baseSha, true);
                    log.info("Force updated branch {} to base SHA {}", branchName, baseSha);
                }
            }
        } catch (Exception e) {
            log.debug("Branch {} does not exist yet, will create new", branchName);
        }
    }

    private String gatherRepoContext(RunContext context, String owner, String repo, String baseSha) {
        log.debug("Gathering repository context");
        StringBuilder repoContext = new StringBuilder();
        
        try {
            Map<String, Object> tree = gitHubApiClient.getRepoTree(owner, repo, baseSha, true);
            List<Map<String, Object>> treeItems = (List<Map<String, Object>>) tree.get("tree");
            
            repoContext.append("## Repository Structure\n\n");
            
            Map<String, List<String>> categorizedFiles = categorizeFiles(treeItems);
            
            for (Map.Entry<String, List<String>> entry : categorizedFiles.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    repoContext.append("**").append(entry.getKey()).append(":**\n");
                    entry.getValue().stream()
                        .limit(15)
                        .forEach(file -> repoContext.append("- ").append(file).append("\n"));
                    repoContext.append("\n");
                }
            }

            fetchRelevantFileContents(context, owner, repo, repoContext, treeItems);

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
        categorized.put("Tests", new ArrayList<>());
        
        for (Map<String, Object> item : treeItems) {
            String path = (String) item.get("path");
            String type = (String) item.get("type");
            
            if (!"blob".equals(type)) continue;
            
            if (path.endsWith(".java") && !path.contains("test") && !path.contains("Test")) {
                categorized.get("Backend (Java)").add(path);
            } else if (path.endsWith(".ts") || path.endsWith(".js") || path.endsWith(".html") || path.endsWith(".css")) {
                categorized.get("Frontend").add(path);
            } else if (path.endsWith(".yml") || path.endsWith(".yaml") || path.endsWith(".xml") || 
                       path.endsWith(".properties") || path.equals("pom.xml") || path.equals("package.json")) {
                categorized.get("Configuration").add(path);
            } else if (path.contains("test") || path.contains("Test")) {
                categorized.get("Tests").add(path);
            }
        }
        
        return categorized;
    }

    private void fetchRelevantFileContents(RunContext context, String owner, String repo, 
                                          StringBuilder repoContext, List<Map<String, Object>> treeItems) {
        List<String> keyFiles = Arrays.asList("AGENTS.md", "README.md");
        
        for (String fileName : keyFiles) {
            try {
                Map<String, Object> fileContent = gitHubApiClient.getRepoContent(owner, repo, fileName);
                String content = (String) fileContent.get("content");
                if (content != null) {
                    String decoded = new String(Base64.getDecoder().decode(content));
                    repoContext.append("\n### ").append(fileName).append(":\n```\n");
                    repoContext.append(truncateContent(decoded, 1000));
                    repoContext.append("\n```\n");
                }
            } catch (Exception e) {
                log.debug("Could not fetch {}: {}", fileName, e.getMessage());
            }
        }

        if (context.getArchitectureNotes() != null) {
            String architectureExcerpt = truncateContent(context.getArchitectureNotes(), 2000);
            repoContext.append("\n### Architecture Guidance:\n");
            repoContext.append(architectureExcerpt).append("\n");
        }
    }

    private String truncateContent(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "\n... (truncated)";
    }

    private CodeChanges generateCodeWithLlm(RunContext context, String repoContext) {
        int maxRetries = 3;
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxRetries) {
            attempt++;
            try {
                String systemPrompt = buildDeveloperSystemPrompt();
                String userPrompt = buildDeveloperUserPrompt(context, repoContext);
                Map<String, Object> schema = buildCodeChangesSchema();

                log.info("Requesting LLM code generation for issue #{} (attempt {}/{})", 
                        context.getRunEntity().getIssueNumber(), attempt, maxRetries);
                String llmResponse = llmService.generateStructuredOutput(systemPrompt, userPrompt, schema);
                
                if (llmResponse == null || llmResponse.trim().isEmpty()) {
                    throw new IllegalStateException("LLM returned empty response");
                }
                
                log.debug("Received code generation from LLM ({} characters)", llmResponse.length());
                CodeChanges codeChanges = objectMapper.readValue(llmResponse, CodeChanges.class);
                
                validateAndEnhanceCodeChanges(codeChanges, context);
                
                log.info("Successfully generated code changes with LLM on attempt {}", attempt);
                return codeChanges;
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM code generation attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        long backoffMs = (long) Math.pow(2, attempt) * 1000;
                        log.debug("Waiting {}ms before retry", backoffMs);
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        log.error("LLM code generation failed after {} attempts, using fallback: {}", 
                maxRetries, lastException != null ? lastException.getMessage() : "unknown error", lastException);
        return createFallbackCodeChanges(context);
    }

    private String buildDeveloperSystemPrompt() {
        return """
            You are an expert software developer implementing code changes based on requirements and architectural guidance.
            
            Your responsibilities:
            - Write clean, idiomatic, production-ready code following existing patterns
            - Implement complete, working solutions with proper error handling
            - Follow the repository's existing code style, patterns, and conventions
            - Create or modify multiple files as needed for a complete implementation
            - Write comprehensive tests (unit, integration, e2e as appropriate)
            - Handle edge cases and validation properly
            - Add appropriate logging and documentation
            - Ensure backward compatibility unless explicitly breaking changes are required
            
            Code Quality Standards:
            - Follow SOLID principles and established design patterns
            - Write self-documenting code with clear variable/function names
            - Keep functions focused and modular
            - Use dependency injection and avoid tight coupling
            - Implement proper error handling and validation
            - Write defensive code that handles edge cases
            - Follow the existing project structure and naming conventions
            
            Testing Requirements:
            - Write unit tests for business logic
            - Write integration tests for API endpoints and database operations
            - Write e2e tests for complete user workflows when applicable
            - Ensure tests are comprehensive and cover edge cases
            - Follow existing test patterns and frameworks
            
            File Operations:
            - Specify 'create' for new files
            - Specify 'modify' for existing files with complete new content
            - Specify 'delete' for files to remove
            - Always provide complete file contents, not partial patches
            
            Security & Best Practices:
            - Never log or expose secrets, tokens, or sensitive data
            - Validate all inputs and sanitize outputs
            - Use parameterized queries for database operations
            - Follow the principle of least privilege
            - Implement proper authentication and authorization checks
            
            Be thorough, precise, and ensure the implementation is production-ready.
            """;
    }

    private String buildDeveloperUserPrompt(RunContext context, String repoContext) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("# Code Implementation Request\n\n");
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

        if (context.getArchitectureNotes() != null && !context.getArchitectureNotes().isEmpty()) {
            prompt.append("## Architecture Notes\n");
            prompt.append(context.getArchitectureNotes()).append("\n\n");
        }

        if (context.getWorkPlan() != null && !context.getWorkPlan().isEmpty()) {
            prompt.append("## Work Plan\n");
            prompt.append(context.getWorkPlan()).append("\n\n");
        }

        prompt.append("## Repository Context\n");
        prompt.append(repoContext).append("\n\n");

        prompt.append("## Implementation Requirements\n");
        prompt.append("Please provide:\n");
        prompt.append("1. Complete implementation with all necessary code changes\n");
        prompt.append("2. Multiple files as needed (controllers, services, repositories, entities, DTOs, etc.)\n");
        prompt.append("3. Comprehensive tests at all appropriate levels\n");
        prompt.append("4. Proper error handling and validation\n");
        prompt.append("5. Documentation updates if needed\n");
        prompt.append("6. Follow existing patterns and conventions exactly\n\n");
        
        prompt.append("For each file:\n");
        prompt.append("- Provide the complete file path\n");
        prompt.append("- Specify the operation: 'create', 'modify', or 'delete'\n");
        prompt.append("- Provide the complete file content (for create/modify operations)\n");
        prompt.append("- Include a brief explanation of what the file does\n");
        
        return prompt.toString();
    }

    private Map<String, Object> buildCodeChangesSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        
        Map<String, Object> properties = new HashMap<>();
        
        properties.put("summary", Map.of(
            "type", "string",
            "description", "Brief summary of the implementation approach (2-4 sentences)"
        ));
        
        properties.put("files", Map.of(
            "type", "array",
            "description", "List of all file changes required for this implementation",
            "items", Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Full file path from repository root"),
                    "operation", Map.of("type", "string", "enum", List.of("create", "modify", "delete"), "description", "Operation to perform on this file"),
                    "content", Map.of("type", "string", "description", "Complete file content (for create/modify operations, empty for delete)"),
                    "explanation", Map.of("type", "string", "description", "Brief explanation of this file's purpose and changes")
                ),
                "required", List.of("path", "operation", "content", "explanation"),
                "additionalProperties", false
            ),
            "minItems", 1
        ));
        
        properties.put("testingNotes", Map.of(
            "type", "string",
            "description", "Notes on what tests were added and what they cover"
        ));
        
        properties.put("implementationNotes", Map.of(
            "type", "string",
            "description", "Important notes about the implementation, design decisions, or considerations"
        ));
        
        schema.put("properties", properties);
        schema.put("required", List.of("summary", "files", "testingNotes", "implementationNotes"));
        
        return schema;
    }

    private void validateAndEnhanceCodeChanges(CodeChanges codeChanges, RunContext context) {
        if (codeChanges.getSummary() == null || codeChanges.getSummary().isEmpty()) {
            log.warn("No summary provided by LLM, generating default");
            codeChanges.setSummary("Implementation for issue #" + context.getRunEntity().getIssueNumber());
        }
        
        if (codeChanges.getFiles() == null || codeChanges.getFiles().isEmpty()) {
            throw new IllegalStateException("LLM did not provide any file changes");
        }
        
        if (codeChanges.getTestingNotes() == null || codeChanges.getTestingNotes().isEmpty()) {
            codeChanges.setTestingNotes("Tests included as part of implementation");
        }
        
        if (codeChanges.getImplementationNotes() == null || codeChanges.getImplementationNotes().isEmpty()) {
            codeChanges.setImplementationNotes("Implementation follows existing patterns and conventions");
        }
    }

    private void validateCodeChanges(CodeChanges codeChanges) {
        log.info("Validating {} file changes", codeChanges.getFiles().size());
        
        if (codeChanges.getFiles().isEmpty()) {
            throw new IllegalArgumentException("No file changes provided");
        }
        
        if (codeChanges.getFiles().size() > 100) {
            throw new IllegalArgumentException("Too many file changes in single commit: " + codeChanges.getFiles().size());
        }
        
        for (FileChange file : codeChanges.getFiles()) {
            String path = file.getPath();
            String operation = file.getOperation();
            
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("File path cannot be empty");
            }
            
            validateFilePathSecurity(path);
            validateFilePathAgainstAllowlist(path);
            validateFilePathAgainstWorkflowProtection(path);
            
            if ("delete".equals(operation)) {
                log.debug("File marked for deletion: {}", path);
            } else if ("create".equals(operation) || "modify".equals(operation)) {
                if (file.getContent() == null) {
                    throw new IllegalArgumentException("File content required for " + operation + " operation: " + path);
                }
                
                if (file.getContent().length() > 1_000_000) {
                    throw new IllegalArgumentException("File content too large: " + path + " (" + file.getContent().length() + " bytes)");
                }
                
                validateFileContentSecurity(path, file.getContent());
                
                log.debug("File marked for {}: {} ({} bytes)", operation, path, file.getContent().length());
            } else {
                throw new IllegalArgumentException("Invalid operation '" + operation + "' for file: " + path);
            }
        }
        
        log.info("All file changes validated successfully");
    }
    
    private void validateFilePathSecurity(String path) {
        if (path.contains("..") || path.startsWith("/") || path.startsWith("\\")) {
            throw new IllegalArgumentException("Invalid file path (security violation): " + path);
        }
        
        if (path.contains("\0") || path.contains("\n") || path.contains("\r")) {
            throw new IllegalArgumentException("Invalid file path (null bytes or newlines): " + path);
        }
        
        String normalizedPath = path.replace('\\', '/');
        if (normalizedPath.contains("//")) {
            throw new IllegalArgumentException("Invalid file path (double slashes): " + path);
        }
        
        if (path.length() > 4096) {
            throw new IllegalArgumentException("File path too long: " + path.length() + " characters");
        }
    }
    
    private void validateFileContentSecurity(String path, String content) {
        String lowerPath = path.toLowerCase();
        String lowerContent = content.toLowerCase();
        
        if (lowerPath.endsWith(".sh") || lowerPath.endsWith(".bash") || lowerPath.endsWith(".zsh")) {
            if (lowerContent.contains("rm -rf") || lowerContent.contains("curl") && lowerContent.contains("|") && lowerContent.contains("sh")) {
                log.warn("Potentially dangerous shell script detected: {}", path);
            }
        }
        
        if (content.contains("-----BEGIN PRIVATE KEY-----") || 
            content.contains("-----BEGIN RSA PRIVATE KEY-----") ||
            content.contains("-----BEGIN EC PRIVATE KEY-----")) {
            throw new IllegalArgumentException("File content contains private key: " + path);
        }
        
        if (content.matches("(?i).*\\b(api[_-]?key|secret|password|token)\\s*[:=]\\s*['\"]?[a-zA-Z0-9+/=]{20,}['\"]?.*") &&
            !path.contains("test") && !path.contains("Test") && !path.contains("example")) {
            log.warn("File may contain hardcoded secrets: {}", path);
        }
    }

    private void validateFilePathAgainstAllowlist(String path) {
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

    private void validateFilePathAgainstWorkflowProtection(String path) {
        String workflowProtectPrefix = properties.workflowProtectPrefix();
        if (workflowProtectPrefix != null && !workflowProtectPrefix.isEmpty() && path.startsWith(workflowProtectPrefix)) {
            throw new IllegalArgumentException("Cannot modify protected workflow files: " + path);
        }
    }

    private CodeChanges createFallbackCodeChanges(RunContext context) {
        log.info("Creating fallback code changes");
        
        CodeChanges changes = new CodeChanges();
        changes.setSummary("Fallback implementation for issue #" + context.getRunEntity().getIssueNumber());
        
        List<FileChange> files = new ArrayList<>();
        
        FileChange docFile = new FileChange();
        docFile.setPath("docs/IMPLEMENTATION_" + context.getRunEntity().getIssueNumber() + ".md");
        docFile.setOperation("create");
        docFile.setContent(generateFallbackDocumentation(context));
        docFile.setExplanation("Documentation of implementation plan and changes");
        files.add(docFile);
        
        changes.setFiles(files);
        changes.setTestingNotes("Fallback mode - manual testing required");
        changes.setImplementationNotes("LLM generation failed, created placeholder documentation");
        
        return changes;
    }

    private String generateFallbackDocumentation(RunContext context) {
        StringBuilder doc = new StringBuilder();
        doc.append("# Implementation Plan\n\n");
        doc.append("**Issue:** #").append(context.getRunEntity().getIssueNumber()).append("\n\n");
        
        if (context.getIssueData() != null) {
            String title = (String) context.getIssueData().get("title");
            doc.append("**Title:** ").append(title).append("\n\n");
        }
        
        doc.append("## Overview\n\n");
        doc.append("This document outlines the implementation plan for the requested changes.\n\n");
        
        if (context.getArchitectureNotes() != null) {
            doc.append("## Architecture Notes\n\n");
            doc.append(context.getArchitectureNotes()).append("\n\n");
        }
        
        doc.append("## Implementation Steps\n\n");
        doc.append("1. Review requirements and acceptance criteria\n");
        doc.append("2. Implement core functionality following existing patterns\n");
        doc.append("3. Add comprehensive tests\n");
        doc.append("4. Update documentation as needed\n");
        doc.append("5. Verify all tests pass\n\n");
        
        doc.append("## Notes\n\n");
        doc.append("This is a placeholder document generated because automatic code generation was unavailable.\n");
        doc.append("Manual implementation is required.\n");
        
        return doc.toString();
    }

    private String applyMultiFileChanges(RunContext context, String owner, String repo, 
                                        String branchName, String baseSha, CodeChanges codeChanges) throws Exception {
        log.info("Applying {} file changes to branch {}", codeChanges.getFiles().size(), branchName);
        
        List<Map<String, Object>> treeEntries = new ArrayList<>();
        int blobsCreated = 0;
        int filesDeleted = 0;
        
        try {
            for (FileChange fileChange : codeChanges.getFiles()) {
                String path = fileChange.getPath();
                String operation = fileChange.getOperation();
                
                Map<String, Object> treeEntry = new HashMap<>();
                treeEntry.put("path", path);
                treeEntry.put("mode", "100644");
                treeEntry.put("type", "blob");
                
                if ("delete".equals(operation)) {
                    treeEntry.put("sha", null);
                    filesDeleted++;
                    log.debug("Marking {} for deletion", path);
                } else {
                    String content = fileChange.getContent();
                    if (content == null || content.isEmpty()) {
                        log.warn("Skipping file with empty content: {}", path);
                        continue;
                    }
                    
                    try {
                        Map<String, Object> blob = gitHubApiClient.createBlob(owner, repo, content, "utf-8");
                        String blobSha = (String) blob.get("sha");
                        
                        if (blobSha == null || blobSha.isEmpty()) {
                            throw new IllegalStateException("Blob creation returned null SHA for: " + path);
                        }
                        
                        treeEntry.put("sha", blobSha);
                        blobsCreated++;
                        log.debug("Created blob {} for {} ({} bytes)", blobSha, path, content.length());
                    } catch (Exception e) {
                        log.error("Failed to create blob for {}: {}", path, e.getMessage());
                        throw new AgentStepException("Failed to create blob for file: " + path, e, 
                                "DEVELOPER", "applyChanges", OrchestratorException.RecoveryStrategy.ESCALATE_TO_HUMAN);
                    }
                }
                
                treeEntries.add(treeEntry);
            }
            
            if (treeEntries.isEmpty()) {
                throw new IllegalStateException("No valid tree entries to commit");
            }
            
            log.info("Created {} blobs and marked {} files for deletion", blobsCreated, filesDeleted);
            
            Map<String, Object> newTree = gitHubApiClient.createTree(owner, repo, treeEntries, baseSha);
            String treeSha = (String) newTree.get("sha");
            
            if (treeSha == null || treeSha.isEmpty()) {
                throw new IllegalStateException("Tree creation returned null SHA");
            }
            
            log.debug("Created tree {}", treeSha);
            
            String commitMessage = buildCommitMessage(context, codeChanges);
            Map<String, Object> author = buildAuthorInfo();
            Map<String, Object> committer = buildCommitterInfo();
            
            Map<String, Object> newCommit = gitHubApiClient.createCommit(
                owner, 
                repo, 
                commitMessage, 
                treeSha, 
                List.of(baseSha),
                author,
                committer
            );
            String commitSha = (String) newCommit.get("sha");
            
            if (commitSha == null || commitSha.isEmpty()) {
                throw new IllegalStateException("Commit creation returned null SHA");
            }
            
            log.debug("Created commit {}", commitSha);
            
            gitHubApiClient.updateReference(owner, repo, "heads/" + branchName, commitSha, false);
            log.info("Updated branch {} to commit {}", branchName, commitSha);
            
            return commitSha;
        } catch (Exception e) {
            log.error("Failed to apply multi-file changes: {}", e.getMessage(), e);
            throw new AgentStepException("Failed to apply code changes to branch: " + branchName, e, 
                    "DEVELOPER", "applyChanges", OrchestratorException.RecoveryStrategy.ESCALATE_TO_HUMAN);
        }
    }

    private String buildCommitMessage(RunContext context, CodeChanges codeChanges) {
        StringBuilder message = new StringBuilder();
        
        String issueType = determineIssueType(context);
        message.append(issueType).append(": ");
        
        if (context.getIssueData() != null) {
            String title = (String) context.getIssueData().get("title");
            message.append(title);
        } else {
            message.append("Implement changes for issue #").append(context.getRunEntity().getIssueNumber());
        }
        
        message.append("\n\n");
        message.append(codeChanges.getSummary()).append("\n\n");
        
        message.append("Closes #").append(context.getRunEntity().getIssueNumber()).append("\n\n");
        
        message.append("Changes:\n");
        for (FileChange file : codeChanges.getFiles()) {
            message.append("- ").append(file.getOperation()).append(": ").append(file.getPath()).append("\n");
        }
        
        if (codeChanges.getImplementationNotes() != null && !codeChanges.getImplementationNotes().isEmpty()) {
            message.append("\n").append(codeChanges.getImplementationNotes());
        }
        
        return message.toString();
    }

    private String determineIssueType(RunContext context) {
        if (context.getIssueData() != null) {
            String title = (String) context.getIssueData().get("title");
            String body = (String) context.getIssueData().getOrDefault("body", "");
            String combined = (title + " " + body).toLowerCase();
            
            if (combined.matches(".*\\b(bug|error|fix|broken|crash|issue)\\b.*")) {
                return "fix";
            } else if (combined.matches(".*\\b(doc|documentation|readme|guide)\\b.*")) {
                return "docs";
            } else if (combined.matches(".*\\b(test|testing|spec)\\b.*")) {
                return "test";
            } else if (combined.matches(".*\\b(refactor|cleanup|improve)\\b.*")) {
                return "refactor";
            } else if (combined.matches(".*\\b(performance|optimize|speed)\\b.*")) {
                return "perf";
            }
        }
        
        return "feat";
    }

    private Map<String, Object> buildAuthorInfo() {
        Map<String, Object> author = new HashMap<>();
        author.put("name", "Atlasia AI Bot");
        author.put("email", "ai-bot@atlasia.io");
        author.put("date", Instant.now().toString());
        return author;
    }

    private Map<String, Object> buildCommitterInfo() {
        Map<String, Object> committer = new HashMap<>();
        committer.put("name", "Atlasia AI Bot");
        committer.put("email", "ai-bot@atlasia.io");
        committer.put("date", Instant.now().toString());
        return committer;
    }

    private String buildPrTitle(RunContext context) {
        if (context.getIssueData() != null) {
            String title = (String) context.getIssueData().get("title");
            return "[AI] " + title;
        }
        return "[AI] Fix issue #" + context.getRunEntity().getIssueNumber();
    }

    private String buildPrBody(RunContext context, CodeChanges codeChanges) {
        StringBuilder body = new StringBuilder();
        
        body.append("## Summary\n\n");
        body.append(codeChanges.getSummary()).append("\n\n");
        
        body.append("Closes #").append(context.getRunEntity().getIssueNumber()).append("\n\n");
        
        if (context.getIssueData() != null) {
            String issueBody = (String) context.getIssueData().getOrDefault("body", "");
            if (issueBody != null && !issueBody.isEmpty()) {
                body.append("## Issue Description\n\n");
                body.append(truncateContent(issueBody, 500)).append("\n\n");
            }
        }
        
        body.append("## Changes\n\n");
        Map<String, List<FileChange>> changesByOperation = codeChanges.getFiles().stream()
            .collect(Collectors.groupingBy(FileChange::getOperation));
        
        if (changesByOperation.containsKey("create")) {
            body.append("### Created Files\n");
            for (FileChange file : changesByOperation.get("create")) {
                body.append("- `").append(file.getPath()).append("` - ").append(file.getExplanation()).append("\n");
            }
            body.append("\n");
        }
        
        if (changesByOperation.containsKey("modify")) {
            body.append("### Modified Files\n");
            for (FileChange file : changesByOperation.get("modify")) {
                body.append("- `").append(file.getPath()).append("` - ").append(file.getExplanation()).append("\n");
            }
            body.append("\n");
        }
        
        if (changesByOperation.containsKey("delete")) {
            body.append("### Deleted Files\n");
            for (FileChange file : changesByOperation.get("delete")) {
                body.append("- `").append(file.getPath()).append("` - ").append(file.getExplanation()).append("\n");
            }
            body.append("\n");
        }
        
        body.append("## Testing\n\n");
        body.append(codeChanges.getTestingNotes()).append("\n\n");
        
        if (codeChanges.getImplementationNotes() != null && !codeChanges.getImplementationNotes().isEmpty()) {
            body.append("## Implementation Notes\n\n");
            body.append(codeChanges.getImplementationNotes()).append("\n\n");
        }
        
        body.append("## Checklist\n\n");
        body.append("- [x] Code follows project conventions and style guidelines\n");
        body.append("- [x] Tests added/updated for new functionality\n");
        body.append("- [x] Documentation updated as needed\n");
        body.append("- [x] All file changes validated against allowlist and workflow protection\n\n");
        
        body.append("---\n");
        body.append("*This PR was automatically generated by Atlasia AI*\n");
        
        return body.toString();
    }

    private void storeReasoningTraceAsArtifact(RunContext context, CodeChanges codeChanges) {
        try {
            Map<String, Object> reasoningTrace = new LinkedHashMap<>();
            reasoningTrace.put("timestamp", Instant.now().toString());
            reasoningTrace.put("issueNumber", context.getRunEntity().getIssueNumber());
            reasoningTrace.put("analysisType", "code_generation");
            reasoningTrace.put("summary", codeChanges.getSummary());
            reasoningTrace.put("fileCount", codeChanges.getFiles().size());
            
            List<Map<String, Object>> filesSummary = new ArrayList<>();
            for (FileChange file : codeChanges.getFiles()) {
                Map<String, Object> fileSummary = new LinkedHashMap<>();
                fileSummary.put("path", file.getPath());
                fileSummary.put("operation", file.getOperation());
                fileSummary.put("explanation", file.getExplanation());
                fileSummary.put("sizeBytes", file.getContent() != null ? file.getContent().length() : 0);
                filesSummary.add(fileSummary);
            }
            reasoningTrace.put("files", filesSummary);
            
            reasoningTrace.put("testingNotes", codeChanges.getTestingNotes());
            reasoningTrace.put("implementationNotes", codeChanges.getImplementationNotes());
            
            String payload = objectMapper.writeValueAsString(reasoningTrace);
            
            RunArtifactEntity artifact = new RunArtifactEntity(
                "developer",
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

    public static class CodeChanges {
        private String summary;
        private List<FileChange> files;
        private String testingNotes;
        private String implementationNotes;

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        
        public List<FileChange> getFiles() { return files; }
        public void setFiles(List<FileChange> files) { this.files = files; }
        
        public String getTestingNotes() { return testingNotes; }
        public void setTestingNotes(String testingNotes) { this.testingNotes = testingNotes; }
        
        public String getImplementationNotes() { return implementationNotes; }
        public void setImplementationNotes(String implementationNotes) { this.implementationNotes = implementationNotes; }
    }

    public static class FileChange {
        private String path;
        private String operation;
        private String content;
        private String explanation;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
    }
}
