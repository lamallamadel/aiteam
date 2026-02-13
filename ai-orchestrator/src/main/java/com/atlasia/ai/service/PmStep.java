package com.atlasia.ai.service;

import com.atlasia.ai.model.TicketPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PmStep implements AgentStep {
    private static final Logger log = LoggerFactory.getLogger(PmStep.class);

    private final GitHubApiClient gitHubApiClient;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public PmStep(GitHubApiClient gitHubApiClient, LlmService llmService, ObjectMapper objectMapper) {
        this.gitHubApiClient = gitHubApiClient;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        Map<String, Object> issueData = gitHubApiClient.readIssue(
                context.getOwner(),
                context.getRepo(),
                context.getRunEntity().getIssueNumber());
        context.setIssueData(issueData);

        String title = (String) issueData.get("title");
        String body = (String) issueData.getOrDefault("body", "");
        List<?> labelsData = (List<?>) issueData.getOrDefault("labels", List.of());

        List<Map<String, Object>> comments = fetchIssueComments(context);

        TicketPlan ticketPlan = generateTicketPlanWithLlm(
                context.getRunEntity().getIssueNumber(),
                title,
                body,
                labelsData,
                comments);

        if (ticketPlan.getLabelsToApply() != null) {
            if (!ticketPlan.getLabelsToApply().isEmpty()) {
                try {
                    gitHubApiClient.addLabelsToIssue(
                            context.getOwner(),
                            context.getRepo(),
                            context.getRunEntity().getIssueNumber(),
                            ticketPlan.getLabelsToApply());
                } catch (Exception e) {
                    String errorMsg = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
                    if (errorMsg.contains("403")) {
                        log.warn(
                                "Skipping label attachment: GitHub token lacks 'write' permission for issues (403 Forbidden). "
                                        +
                                        "The workflow will continue without labels.");
                    } else {
                        log.warn("Failed to add labels to issue ({}). Continuing workflow...", errorMsg);
                    }
                }
            }
        }

        return objectMapper.writeValueAsString(ticketPlan);
    }

    private List<Map<String, Object>> fetchIssueComments(RunContext context) {
        try {
            return gitHubApiClient.listIssueComments(
                    context.getOwner(),
                    context.getRepo(),
                    context.getRunEntity().getIssueNumber());
        } catch (Exception e) {
            log.warn("Failed to fetch issue comments: {}", e.getMessage());
            return List.of();
        }
    }

    private TicketPlan generateTicketPlanWithLlm(int issueNumber, String title, String body,
            List<?> labelsData, List<Map<String, Object>> comments) {
        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(issueNumber, title, body, labelsData, comments);
            Map<String, Object> schema = buildTicketPlanSchema();

            log.info("Sending request to LLM for ticket plan generation for issue #{}", issueNumber);
            String llmResponse = llmService.generateStructuredOutput(systemPrompt, userPrompt, schema);

            log.debug("Received LLM response: {}", llmResponse);
            TicketPlan plan = parseAndValidateLlmResponse(llmResponse, issueNumber, title, body);

            validateAndEnhancePlan(plan, issueNumber, title, body, comments);

            log.info("Successfully generated ticket plan with LLM for issue #{}", issueNumber);
            return plan;
        } catch (Exception e) {
            log.error("LLM generation failed for issue #{}, using fallback strategy: {}", issueNumber, e.getMessage(),
                    e);
            return generateFallbackTicketPlan(issueNumber, title, body, labelsData, comments);
        }
    }

    private String buildSystemPrompt() {
        return """
                You are a product manager analyzing GitHub issues to create structured ticket plans.
                Your job is to extract key information from the issue and create a comprehensive plan.

                Focus on:
                - Understanding the core requirements and user needs from the issue description
                - Identifying clear, specific, and testable acceptance criteria that define success
                - Recognizing scope boundaries and what's explicitly out of scope or future work
                - Identifying potential risks, technical challenges, dependencies, and blockers
                - Suggesting appropriate labels based on issue type, complexity, priority, and domain
                - Extracting information from comments that clarify requirements or add context

                Be thorough but concise. Extract information directly from the issue when possible.
                If acceptance criteria are not explicitly stated, infer them from the requirements.
                Ensure all criteria are specific, measurable, and actionable.
                """;
    }

    private String buildUserPrompt(int issueNumber, String title, String body,
            List<?> labelsData, List<Map<String, Object>> comments) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following GitHub issue and create a structured ticket plan:\n\n");
        prompt.append("Issue #").append(issueNumber).append(": ").append(title).append("\n\n");

        if (body != null && !body.isEmpty()) {
            prompt.append("Description:\n").append(body).append("\n\n");
        } else {
            prompt.append("Description: (no description provided)\n\n");
        }

        if (!labelsData.isEmpty()) {
            prompt.append("Existing Labels: ");
            prompt.append(labelsData.stream()
                    .map(label -> {
                        if (label instanceof Map) {
                            return (String) ((Map<?, ?>) label).get("name");
                        }
                        return label.toString();
                    })
                    .collect(Collectors.joining(", ")));
            prompt.append("\n\n");
        }

        if (!comments.isEmpty()) {
            prompt.append("Comments (additional context):\n");
            int commentCount = 0;
            for (Map<String, Object> comment : comments) {
                if (commentCount >= 10)
                    break;

                String commentBody = (String) comment.get("body");
                Map<String, Object> user = (Map<String, Object>) comment.get("user");
                String username = user != null ? (String) user.get("login") : "unknown";

                if (commentBody != null && !commentBody.isEmpty() && !commentBody.startsWith("<!--")) {
                    prompt.append("- @").append(username).append(": ");
                    int maxLength = 300;
                    if (commentBody.length() > maxLength) {
                        prompt.append(commentBody.substring(0, maxLength)).append("...");
                    } else {
                        prompt.append(commentBody);
                    }
                    prompt.append("\n");
                    commentCount++;
                }
            }
            if (commentCount > 0) {
                prompt.append("\n");
            }
        }

        prompt.append("Create a comprehensive ticket plan with:\n");
        prompt.append("- A clear summary of what needs to be done\n");
        prompt.append("- Specific, testable acceptance criteria that define when this is complete\n");
        prompt.append("- Items that are explicitly out of scope or deferred to future work\n");
        prompt.append("- Potential risks, challenges, dependencies, and blockers\n");
        prompt.append("- Appropriate labels for categorization and prioritization\n");

        return prompt.toString();
    }

    private Map<String, Object> buildTicketPlanSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> properties = new HashMap<>();

        properties.put("issueId", Map.of(
                "type", "integer",
                "description", "The GitHub issue number"));

        properties.put("title", Map.of(
                "type", "string",
                "description", "The issue title"));

        properties.put("summary", Map.of(
                "type", "string",
                "description", "A concise summary of the requirements and goals (2-4 sentences)"));

        properties.put("acceptanceCriteria", Map.of(
                "type", "array",
                "description",
                "List of specific, testable criteria that define completion. Each criterion should be clear and verifiable.",
                "items", Map.of("type", "string"),
                "minItems", 1));

        properties.put("outOfScope", Map.of(
                "type", "array",
                "description", "List of items explicitly not included in this work or deferred to future iterations",
                "items", Map.of("type", "string")));

        properties.put("risks", Map.of(
                "type", "array",
                "description", "List of potential risks, technical challenges, dependencies, or blockers",
                "items", Map.of("type", "string")));

        properties.put("labelsToApply", Map.of(
                "type", "array",
                "description",
                "Suggested labels for the issue (e.g., bug, enhancement, documentation, high-priority, backend, frontend)",
                "items", Map.of("type", "string"),
                "minItems", 1));

        schema.put("properties", properties);
        schema.put("required", List.of("issueId", "title", "summary", "acceptanceCriteria",
                "outOfScope", "risks", "labelsToApply"));

        return schema;
    }

    private TicketPlan parseAndValidateLlmResponse(String llmResponse, int issueNumber,
            String title, String body) throws JsonProcessingException {
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

        TicketPlan plan;
        try {
            plan = objectMapper.readValue(cleanedResponse, TicketPlan.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM response as JSON: {}", e.getMessage());
            throw e;
        }

        if (plan.getIssueId() != issueNumber && plan.getIssueId() != 0) {
            log.warn("LLM returned different issue ID ({}) than expected ({})", plan.getIssueId(), issueNumber);
        }

        if (plan.getTitle() != null && !plan.getTitle().equals(title)) {
            log.debug("LLM returned different title than original");
        }

        return plan;
    }

    private void validateAndEnhancePlan(TicketPlan plan, int issueNumber, String title,
            String body, List<Map<String, Object>> comments) {
        if (plan.getIssueId() == 0) {
            plan.setIssueId(issueNumber);
        }

        if (plan.getTitle() == null || plan.getTitle().isEmpty()) {
            plan.setTitle(title);
        }

        if (plan.getLabelsToApply() == null) {
            plan.setLabelsToApply(suggestLabels(title, body));
        }

        if (plan.getSummary() == null || plan.getSummary().isEmpty()) {
            log.warn("LLM did not provide summary, generating fallback");
            plan.setSummary(generateSummary(title, body));
        }

        if (plan.getAcceptanceCriteria() == null || plan.getAcceptanceCriteria().isEmpty()) {
            log.warn("LLM did not provide acceptance criteria, extracting with NLP");
            plan.setAcceptanceCriteria(extractAcceptanceCriteriaWithNlp(body, comments));
        } else {
            List<String> enhancedCriteria = enhanceAcceptanceCriteria(plan.getAcceptanceCriteria(), body, comments);
            plan.setAcceptanceCriteria(enhancedCriteria);
        }

        if (plan.getOutOfScope() == null) {
            plan.setOutOfScope(extractOutOfScopeWithNlp(body, comments));
        } else if (plan.getOutOfScope().isEmpty()) {
            List<String> extracted = extractOutOfScopeWithNlp(body, comments);
            if (!extracted.isEmpty()) {
                plan.setOutOfScope(extracted);
            }
        }

        if (plan.getRisks() == null || plan.getRisks().isEmpty()) {
            log.warn("LLM did not provide risks, extracting with NLP");
            plan.setRisks(extractRisksWithNlp(body, comments));
        } else {
            List<String> enhancedRisks = enhanceRisks(plan.getRisks(), body, comments);
            plan.setRisks(enhancedRisks);
        }

        if (plan.getLabelsToApply() == null || plan.getLabelsToApply().isEmpty()) {
            log.warn("LLM did not provide labels, suggesting with heuristics");
            plan.setLabelsToApply(suggestLabels(title, body));
        } else {
            List<String> enhancedLabels = enhanceLabels(plan.getLabelsToApply(), title, body);
            plan.setLabelsToApply(enhancedLabels);
        }
    }

    private TicketPlan generateFallbackTicketPlan(int issueNumber, String title, String body,
            List<?> labelsData, List<Map<String, Object>> comments) {
        log.info("Generating fallback ticket plan for issue #{}", issueNumber);

        TicketPlan plan = new TicketPlan();
        plan.setIssueId(issueNumber);
        plan.setTitle(title);
        plan.setSummary(generateSummary(title, body));
        plan.setAcceptanceCriteria(extractAcceptanceCriteriaWithNlp(body, comments));
        plan.setOutOfScope(extractOutOfScopeWithNlp(body, comments));
        plan.setRisks(extractRisksWithNlp(body, comments));
        plan.setLabelsToApply(suggestLabels(title, body));

        return plan;
    }

    private String generateSummary(String title, String body) {
        if (body == null || body.isEmpty()) {
            return title;
        }

        String cleanedBody = body
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("\\[.*?\\]\\(.*?\\)", "")
                .replaceAll("#{1,6}\\s+", "")
                .replaceAll("<!--[\\s\\S]*?-->", "")
                .trim();

        String[] lines = cleanedBody.split("\n");
        StringBuilder summary = new StringBuilder();
        int charCount = 0;
        int maxChars = 400;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty())
                continue;

            if (charCount + line.length() > maxChars) {
                if (charCount > 0) {
                    summary.append("...");
                }
                break;
            }

            if (summary.length() > 0) {
                summary.append(" ");
            }
            summary.append(line);
            charCount += line.length();
        }

        return summary.length() > 0 ? summary.toString() : title;
    }

    private List<String> extractAcceptanceCriteriaWithNlp(String body, List<Map<String, Object>> comments) {
        if (body == null) {
            body = "";
        }

        List<String> criteria = new ArrayList<>();

        String combinedText = body;
        if (comments != null && !comments.isEmpty()) {
            StringBuilder commentText = new StringBuilder();
            for (Map<String, Object> comment : comments) {
                String commentBody = (String) comment.get("body");
                if (commentBody != null && !commentBody.isEmpty()) {
                    commentText.append("\n").append(commentBody);
                }
            }
            combinedText = body + commentText.toString();
        }

        Pattern sectionPattern = Pattern.compile(
                "(?i)(?:^|\\n)\\s*(?:##?\\s+)?(?:acceptance criteria|AC|success criteria|definition of done|DoD|checklist|requirements|tasks?):?\\s*\\n([\\s\\S]*?)(?=\\n\\s*##?\\s+[A-Z]|\\z)",
                Pattern.CASE_INSENSITIVE);
        Matcher sectionMatcher = sectionPattern.matcher(combinedText);

        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(1);
            List<String> extracted = extractListItems(section);
            criteria.addAll(extracted);
            log.debug("Extracted {} criteria from explicit section", extracted.size());
        }

        Pattern checkboxPattern = Pattern.compile("^\\s*[-*]?\\s*\\[[ xX]\\]\\s+(.+)$", Pattern.MULTILINE);
        Matcher checkboxMatcher = checkboxPattern.matcher(combinedText);
        while (checkboxMatcher.find()) {
            String item = checkboxMatcher.group(1).trim();
            if (!item.isEmpty() && item.length() > 5 && !containsSimilar(criteria, item)) {
                criteria.add(item);
            }
        }

        Pattern imperativePattern = Pattern.compile(
                "(?:^|\\n)\\s*[-*]?\\s*(?:should|must|needs? to|has to|will|shall)\\s+([^.!?\\n]{15,150})(?:[.!?]|\\n|\\z)",
                Pattern.CASE_INSENSITIVE);
        Matcher imperativeMatcher = imperativePattern.matcher(body);
        int imperativeCount = 0;
        while (imperativeMatcher.find() && imperativeCount < 5) {
            String requirement = imperativeMatcher.group(0).trim();
            if (!containsSimilar(criteria, requirement)) {
                criteria.add(requirement);
                imperativeCount++;
            }
        }

        Pattern givenWhenThenPattern = Pattern.compile(
                "(?i)(?:Given|When|Then)\\s+([^\\n]{10,150})",
                Pattern.CASE_INSENSITIVE);
        Matcher givenWhenThenMatcher = givenWhenThenPattern.matcher(body);
        while (givenWhenThenMatcher.find()) {
            String bddCriteria = givenWhenThenMatcher.group(0).trim();
            if (!containsSimilar(criteria, bddCriteria)) {
                criteria.add(bddCriteria);
            }
        }

        if (criteria.isEmpty()) {
            criteria.add("Implementation matches issue requirements");
            criteria.add("All tests pass");
            criteria.add("Code follows project conventions");
        }

        return criteria.stream()
                .distinct()
                .limit(15)
                .collect(Collectors.toList());
    }

    private List<String> enhanceAcceptanceCriteria(List<String> existingCriteria,
            String body, List<Map<String, Object>> comments) {
        List<String> nlpCriteria = extractAcceptanceCriteriaWithNlp(body, comments);

        Set<String> criteriaSet = new LinkedHashSet<>(existingCriteria);

        for (String nlpItem : nlpCriteria) {
            if (!containsSimilar(new ArrayList<>(criteriaSet), nlpItem)) {
                criteriaSet.add(nlpItem);
            }
        }

        return new ArrayList<>(criteriaSet).stream()
                .limit(15)
                .collect(Collectors.toList());
    }

    private List<String> extractOutOfScopeWithNlp(String body, List<Map<String, Object>> comments) {
        if (body == null) {
            body = "";
        }

        List<String> outOfScope = new ArrayList<>();

        String combinedText = body;
        if (comments != null && !comments.isEmpty()) {
            StringBuilder commentText = new StringBuilder();
            for (Map<String, Object> comment : comments) {
                String commentBody = (String) comment.get("body");
                if (commentBody != null && !commentBody.isEmpty()) {
                    commentText.append("\n").append(commentBody);
                }
            }
            combinedText = body + commentText.toString();
        }

        Pattern sectionPattern = Pattern.compile(
                "(?i)(?:^|\\n)\\s*(?:##?\\s+)?(?:out of scope|not in scope|explicitly excluded|won't do|won't fix|future work|deferred|not included):?\\s*\\n([\\s\\S]*?)(?=\\n\\s*##?\\s+[A-Z]|\\z)",
                Pattern.CASE_INSENSITIVE);
        Matcher sectionMatcher = sectionPattern.matcher(combinedText);

        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(1);
            outOfScope.addAll(extractListItems(section));
        }

        Pattern notPattern = Pattern.compile(
                "(?:will not|won't|should not|shouldn't|doesn't include|does not include|not included|excluding|excluded|out of scope)\\s+([^.!?\\n]{10,150})",
                Pattern.CASE_INSENSITIVE);
        Matcher notMatcher = notPattern.matcher(combinedText);
        int notCount = 0;
        while (notMatcher.find() && notCount < 5) {
            String item = notMatcher.group(0).trim();
            if (!containsSimilar(outOfScope, item)) {
                outOfScope.add(item);
                notCount++;
            }
        }

        Pattern futurePattern = Pattern.compile(
                "(?:future|later|next version|v\\d+\\.\\d+|phase \\d+|deferred|postponed?)\\s*:?\\s*([^.!?\\n]{10,150})",
                Pattern.CASE_INSENSITIVE);
        Matcher futureMatcher = futurePattern.matcher(combinedText);
        int futureCount = 0;
        while (futureMatcher.find() && futureCount < 3) {
            String item = futureMatcher.group(0).trim();
            if (!containsSimilar(outOfScope, item)) {
                outOfScope.add(item);
                futureCount++;
            }
        }

        return outOfScope.stream()
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<String> extractRisksWithNlp(String body, List<Map<String, Object>> comments) {
        if (body == null) {
            body = "";
        }

        List<String> risks = new ArrayList<>();

        String combinedText = body;
        if (comments != null && !comments.isEmpty()) {
            StringBuilder commentText = new StringBuilder();
            for (Map<String, Object> comment : comments) {
                String commentBody = (String) comment.get("body");
                if (commentBody != null && !commentBody.isEmpty()) {
                    commentText.append("\n").append(commentBody);
                }
            }
            combinedText = body + commentText.toString();
        }

        Pattern sectionPattern = Pattern.compile(
                "(?i)(?:^|\\n)\\s*(?:##?\\s+)?(?:risks?|challenges?|concerns?|blockers?|dependencies|considerations?|warnings?|caveats?):?\\s*\\n([\\s\\S]*?)(?=\\n\\s*##?\\s+[A-Z]|\\z)",
                Pattern.CASE_INSENSITIVE);
        Matcher sectionMatcher = sectionPattern.matcher(combinedText);

        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(1);
            risks.addAll(extractListItems(section));
        }

        Pattern riskPattern = Pattern.compile(
                "(?:might|may|could|risk of|concern about|potential issue|depends on|dependent on|blocked by|requires?|needs?|warning|caveat|difficult to|hard to|challenge)\\s+([^.!?\\n]{10,150})",
                Pattern.CASE_INSENSITIVE);
        Matcher riskMatcher = riskPattern.matcher(combinedText);
        int riskCount = 0;
        while (riskMatcher.find() && riskCount < 5) {
            String risk = riskMatcher.group(0).trim();
            if (!containsSimilar(risks, risk)) {
                risks.add(risk);
                riskCount++;
            }
        }

        Pattern complexityPattern = Pattern.compile(
                "(?i)\\b(complex|complicated|difficult|challenging|hard to|tricky|non-trivial|intricate)\\b");
        if (complexityPattern.matcher(body).find() && !containsKeyword(risks, "complex")) {
            risks.add("Implementation may be complex or require careful design");
        }

        Pattern performancePattern = Pattern.compile(
                "(?i)\\b(performance|slow|latency|scalability|scale|optimization|efficient)\\b");
        if (performancePattern.matcher(body).find() && !containsKeyword(risks, "performance")) {
            risks.add("Performance considerations may require optimization");
        }

        Pattern securityPattern = Pattern.compile(
                "(?i)\\b(security|vulnerability|auth|authentication|authorization|encryption|sensitive)\\b");
        if (securityPattern.matcher(body).find() && !containsKeyword(risks, "security")) {
            risks.add("Security implications require careful review");
        }

        Pattern breakingPattern = Pattern.compile(
                "(?i)\\b(breaking change|backwards? compatibility|migration|deprecat)\\b");
        if (breakingPattern.matcher(body).find() && !containsKeyword(risks, "breaking")) {
            risks.add("Breaking changes may affect existing functionality");
        }

        return risks.stream()
                .distinct()
                .limit(12)
                .collect(Collectors.toList());
    }

    private List<String> enhanceRisks(List<String> existingRisks, String body, List<Map<String, Object>> comments) {
        List<String> nlpRisks = extractRisksWithNlp(body, comments);

        Set<String> risksSet = new LinkedHashSet<>(existingRisks);

        for (String nlpRisk : nlpRisks) {
            if (!containsSimilar(new ArrayList<>(risksSet), nlpRisk)) {
                risksSet.add(nlpRisk);
            }
        }

        return new ArrayList<>(risksSet).stream()
                .limit(12)
                .collect(Collectors.toList());
    }

    private List<String> extractListItems(String text) {
        List<String> items = new ArrayList<>();

        Pattern listPattern = Pattern.compile("^\\s*[-*+•]\\s+(.+)$", Pattern.MULTILINE);
        Matcher listMatcher = listPattern.matcher(text);

        while (listMatcher.find()) {
            String item = listMatcher.group(1).trim();
            item = item.replaceAll("\\[[ xX]\\]\\s*", "");
            if (!item.isEmpty() && item.length() > 3 && item.length() < 400) {
                items.add(item);
            }
        }

        Pattern numberedPattern = Pattern.compile("^\\s*\\d+\\.\\s+(.+)$", Pattern.MULTILINE);
        Matcher numberedMatcher = numberedPattern.matcher(text);

        while (numberedMatcher.find()) {
            String item = numberedMatcher.group(1).trim();
            if (!item.isEmpty() && item.length() > 3 && item.length() < 400 && !containsSimilar(items, item)) {
                items.add(item);
            }
        }

        return items;
    }

    private List<String> suggestLabels(String title, String body) {
        List<String> labels = new ArrayList<>();
        labels.add("ai-generated");

        String combined = (title + " " + (body != null ? body : "")).toLowerCase();

        if (combined.matches(".*\\b(bug|error|issue|broken|fix|crash|fail|defect)\\b.*")) {
            labels.add("bug");
        } else if (combined.matches(".*\\b(feature|enhancement|add|new|implement|support)\\b.*")) {
            labels.add("enhancement");
        }

        if (combined.matches(".*\\b(doc|documentation|readme|guide|tutorial|comment)\\b.*")) {
            labels.add("documentation");
        }

        if (combined.matches(".*\\b(test|testing|spec|e2e|integration|unit test)\\b.*")) {
            labels.add("testing");
        }

        if (combined.matches(".*\\b(refactor|cleanup|improve|optimize|performance|reorganize)\\b.*")) {
            labels.add("refactoring");
        }

        if (combined.matches(".*\\b(security|vulnerability|auth|permission|encrypt|cve)\\b.*")) {
            labels.add("security");
        }

        if (combined.matches(".*\\b(urgent|critical|blocker|asap|high priority|p0|p1)\\b.*")) {
            labels.add("high-priority");
        }

        if (combined.matches(".*\\b(backend|api|server|database|service)\\b.*")) {
            labels.add("backend");
        }

        if (combined.matches(".*\\b(frontend|ui|ux|interface|component|css|html)\\b.*")) {
            labels.add("frontend");
        }

        if (combined.matches(".*\\b(infrastructure|deploy|ci|cd|docker|kubernetes)\\b.*")) {
            labels.add("infrastructure");
        }

        return labels.stream().distinct().collect(Collectors.toList());
    }

    private List<String> enhanceLabels(List<String> existingLabels, String title, String body) {
        List<String> suggestedLabels = suggestLabels(title, body);

        Set<String> labelsSet = new LinkedHashSet<>(existingLabels);

        for (String suggested : suggestedLabels) {
            if (!labelsSet.contains(suggested.toLowerCase()) &&
                    labelsSet.stream().noneMatch(l -> l.equalsIgnoreCase(suggested))) {
                labelsSet.add(suggested);
            }
        }

        return new ArrayList<>(labelsSet);
    }

    private boolean containsSimilar(List<String> list, String item) {
        if (list == null || item == null) {
            return false;
        }

        String normalizedItem = item.toLowerCase().replaceAll("\\s+", " ").trim();

        for (String existing : list) {
            String normalizedExisting = existing.toLowerCase().replaceAll("\\s+", " ").trim();

            if (normalizedExisting.equals(normalizedItem)) {
                return true;
            }

            if (normalizedExisting.length() > 20 && normalizedItem.length() > 20) {
                if (normalizedExisting.contains(normalizedItem) || normalizedItem.contains(normalizedExisting)) {
                    return true;
                }

                double similarity = calculateSimilarity(normalizedExisting, normalizedItem);
                if (similarity > 0.85) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean containsKeyword(List<String> list, String keyword) {
        if (list == null || keyword == null) {
            return false;
        }

        String normalizedKeyword = keyword.toLowerCase();

        for (String item : list) {
            if (item.toLowerCase().contains(normalizedKeyword)) {
                return true;
            }
        }

        return false;
    }

    private double calculateSimilarity(String s1, String s2) {
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) {
            return 1.0;
        }

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLength);
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }

        return dp[s1.length()][s2.length()];
    }
}
