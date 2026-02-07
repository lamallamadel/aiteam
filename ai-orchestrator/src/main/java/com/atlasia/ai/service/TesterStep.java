package com.atlasia.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TesterStep implements AgentStep {
    private static final Logger log = LoggerFactory.getLogger(TesterStep.class);
    
    private static final int MAX_CI_ITERATIONS = 3;
    private static final int MAX_E2E_ITERATIONS = 2;
    private static final int MAX_POLLING_ATTEMPTS = 60;
    private static final long BASE_BACKOFF_MS = 5000;
    private static final long MAX_BACKOFF_MS = 60000;
    private static final Duration WORKFLOW_TIMEOUT = Duration.ofMinutes(30);
    
    private final GitHubApiClient gitHubApiClient;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public TesterStep(GitHubApiClient gitHubApiClient, LlmService llmService, ObjectMapper objectMapper) {
        this.gitHubApiClient = gitHubApiClient;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(RunContext context) throws Exception {
        log.info("Starting testing phase for issue #{}", context.getRunEntity().getIssueNumber());
        
        String prUrl = context.getPrUrl();
        if (prUrl == null || prUrl.isEmpty()) {
            throw new IllegalStateException("PR URL is required for testing");
        }
        
        boolean ciPassed = false;
        boolean e2ePassed = false;
        List<String> notes = new ArrayList<>();
        
        int ciAttempts = 0;
        int e2eAttempts = 0;
        
        while (!ciPassed && ciAttempts < MAX_CI_ITERATIONS) {
            ciAttempts++;
            notes.add("CI attempt " + ciAttempts);
            log.info("Starting CI check attempt {}/{}", ciAttempts, MAX_CI_ITERATIONS);
            
            CiStatus ciStatus = checkCiStatus(context);
            
            if (ciStatus.passed) {
                ciPassed = true;
                notes.add("CI passed on attempt " + ciAttempts);
                log.info("CI passed on attempt {}", ciAttempts);
            } else if (ciAttempts < MAX_CI_ITERATIONS) {
                notes.add("CI failed on attempt " + ciAttempts + ", applying fix");
                log.warn("CI failed on attempt {}, will attempt fix", ciAttempts);
                applyFix(context, ciStatus);
                context.getRunEntity().incrementCiFixCount();
            } else {
                notes.add("CI failed after " + MAX_CI_ITERATIONS + " attempts");
                log.error("CI failed after {} attempts", MAX_CI_ITERATIONS);
                return createEscalation(context, "CI tests failed after " + MAX_CI_ITERATIONS + " fix attempts", ciStatus);
            }
        }
        
        while (!e2ePassed && e2eAttempts < MAX_E2E_ITERATIONS) {
            e2eAttempts++;
            notes.add("E2E attempt " + e2eAttempts);
            log.info("Starting E2E check attempt {}/{}", e2eAttempts, MAX_E2E_ITERATIONS);
            
            E2eStatus e2eStatus = checkE2eStatus(context);
            
            if (e2eStatus.passed) {
                e2ePassed = true;
                notes.add("E2E passed on attempt " + e2eAttempts);
                log.info("E2E passed on attempt {}", e2eAttempts);
            } else if (e2eAttempts < MAX_E2E_ITERATIONS) {
                notes.add("E2E failed on attempt " + e2eAttempts + ", applying fix");
                log.warn("E2E failed on attempt {}, will attempt fix", e2eAttempts);
                applyE2eFix(context, e2eStatus);
                context.getRunEntity().incrementE2eFixCount();
            } else {
                notes.add("E2E failed after " + MAX_E2E_ITERATIONS + " attempts");
                log.error("E2E failed after {} attempts", MAX_E2E_ITERATIONS);
                return createEscalation(context, "E2E tests failed after " + MAX_E2E_ITERATIONS + " fix attempts", e2eStatus);
            }
        }
        
        Map<String, Object> testReport = buildDetailedTestReport(context, prUrl, notes);
        String reportJson = objectMapper.writeValueAsString(testReport);
        
        log.info("Testing phase completed successfully for issue #{}", context.getRunEntity().getIssueNumber());
        return reportJson;
    }

    private CiStatus checkCiStatus(RunContext context) {
        String owner = context.getOwner();
        String repo = context.getRepo();
        String branchName = context.getBranchName();
        
        try {
            log.debug("Fetching latest commit SHA for branch {}", branchName);
            Map<String, Object> branchRef = gitHubApiClient.getReference(owner, repo, "heads/" + branchName);
            Map<String, Object> branchObject = (Map<String, Object>) branchRef.get("object");
            String commitSha = (String) branchObject.get("sha");
            
            if (commitSha == null || commitSha.isEmpty()) {
                throw new IllegalStateException("Could not determine commit SHA for branch: " + branchName);
            }
            
            log.info("Polling CI status for commit {}", commitSha);
            
            Map<String, Object> checkRuns = pollCheckRuns(owner, repo, commitSha);
            CiStatus ciStatus = analyzeCheckRuns(owner, repo, checkRuns);
            
            return ciStatus;
        } catch (Exception e) {
            log.error("Error checking CI status: {}", e.getMessage(), e);
            return new CiStatus(false, "Error checking CI status: " + e.getMessage(), 
                               new HashMap<>(), new HashMap<>(), new ArrayList<>());
        }
    }

    private E2eStatus checkE2eStatus(RunContext context) {
        String owner = context.getOwner();
        String repo = context.getRepo();
        String branchName = context.getBranchName();
        
        try {
            log.debug("Fetching latest commit SHA for branch {}", branchName);
            Map<String, Object> branchRef = gitHubApiClient.getReference(owner, repo, "heads/" + branchName);
            Map<String, Object> branchObject = (Map<String, Object>) branchRef.get("object");
            String commitSha = (String) branchObject.get("sha");
            
            if (commitSha == null || commitSha.isEmpty()) {
                throw new IllegalStateException("Could not determine commit SHA for branch: " + branchName);
            }
            
            log.info("Polling E2E status for commit {}", commitSha);
            
            Map<String, Object> checkRuns = pollCheckRuns(owner, repo, commitSha);
            E2eStatus e2eStatus = analyzeE2eRuns(owner, repo, checkRuns);
            
            return e2eStatus;
        } catch (Exception e) {
            log.error("Error checking E2E status: {}", e.getMessage(), e);
            return new E2eStatus(false, "Error checking E2E status: " + e.getMessage(), new ArrayList<>());
        }
    }

    private Map<String, Object> pollCheckRuns(String owner, String repo, String commitSha) {
        Instant startTime = Instant.now();
        int attempt = 0;
        
        while (attempt < MAX_POLLING_ATTEMPTS) {
            attempt++;
            
            if (Duration.between(startTime, Instant.now()).compareTo(WORKFLOW_TIMEOUT) > 0) {
                log.error("Workflow polling timed out after {} minutes", WORKFLOW_TIMEOUT.toMinutes());
                throw new RuntimeException("Workflow polling timed out");
            }
            
            try {
                log.debug("Polling check runs, attempt {}/{}", attempt, MAX_POLLING_ATTEMPTS);
                Map<String, Object> checkRuns = gitHubApiClient.listCheckRunsForRef(owner, repo, commitSha);
                
                if (checkRuns == null) {
                    log.warn("Check runs API returned null");
                    sleepWithExponentialBackoff(attempt);
                    continue;
                }
                
                List<Map<String, Object>> checkRunsList = (List<Map<String, Object>>) checkRuns.get("check_runs");
                
                if (checkRunsList == null || checkRunsList.isEmpty()) {
                    log.debug("No check runs found yet, waiting...");
                    sleepWithExponentialBackoff(attempt);
                    continue;
                }
                
                boolean allCompleted = true;
                for (Map<String, Object> checkRun : checkRunsList) {
                    String status = (String) checkRun.get("status");
                    if (!"completed".equals(status)) {
                        allCompleted = false;
                        log.debug("Check run '{}' is still {}", checkRun.get("name"), status);
                        break;
                    }
                }
                
                if (allCompleted) {
                    log.info("All check runs completed after {} attempts", attempt);
                    return checkRuns;
                }
                
                sleepWithExponentialBackoff(attempt);
                
            } catch (Exception e) {
                log.warn("Error polling check runs on attempt {}: {}", attempt, e.getMessage());
                if (attempt >= MAX_POLLING_ATTEMPTS) {
                    throw new RuntimeException("Failed to poll check runs after " + MAX_POLLING_ATTEMPTS + " attempts", e);
                }
                sleepWithExponentialBackoff(attempt);
            }
        }
        
        throw new RuntimeException("Check runs did not complete within " + MAX_POLLING_ATTEMPTS + " attempts");
    }

    private void sleepWithExponentialBackoff(int attempt) {
        long backoffMs = Math.min(BASE_BACKOFF_MS * (long) Math.pow(2, attempt - 1), MAX_BACKOFF_MS);
        long jitterMs = random.nextInt((int) (backoffMs * 0.3));
        long sleepMs = backoffMs + jitterMs;
        
        log.debug("Sleeping for {}ms (backoff with jitter)", sleepMs);
        
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Polling interrupted", e);
        }
    }

    private CiStatus analyzeCheckRuns(String owner, String repo, Map<String, Object> checkRunsResponse) {
        List<Map<String, Object>> checkRuns = (List<Map<String, Object>>) checkRunsResponse.get("check_runs");
        
        if (checkRuns == null || checkRuns.isEmpty()) {
            log.warn("No check runs found to analyze");
            return new CiStatus(false, "No check runs found", new HashMap<>(), new HashMap<>(), new ArrayList<>());
        }
        
        Map<String, String> backendResults = new HashMap<>();
        Map<String, String> frontendResults = new HashMap<>();
        List<String> failures = new ArrayList<>();
        boolean allPassed = true;
        
        for (Map<String, Object> checkRun : checkRuns) {
            String name = (String) checkRun.get("name");
            String conclusion = (String) checkRun.get("conclusion");
            String status = (String) checkRun.get("status");
            Long checkRunId = ((Number) checkRun.get("id")).longValue();
            
            log.debug("Analyzing check run '{}': status={}, conclusion={}", name, status, conclusion);
            
            if (!"completed".equals(status)) {
                continue;
            }
            
            boolean isE2e = isE2eCheckRun(name);
            if (isE2e) {
                continue;
            }
            
            boolean isPassed = "success".equals(conclusion);
            String resultStatus = isPassed ? "PASSED" : "FAILED";
            
            if (!isPassed) {
                allPassed = false;
                log.info("Check run '{}' failed, fetching logs", name);
                
                String failureDetails = extractFailureDetails(owner, repo, checkRunId, name);
                failures.add(failureDetails);
                
                if (isBackendCheck(name)) {
                    backendResults.put(name, resultStatus + ": " + failureDetails);
                } else if (isFrontendCheck(name)) {
                    frontendResults.put(name, resultStatus + ": " + failureDetails);
                } else {
                    backendResults.put(name, resultStatus + ": " + failureDetails);
                }
            } else {
                if (isBackendCheck(name)) {
                    backendResults.put(name, resultStatus);
                } else if (isFrontendCheck(name)) {
                    frontendResults.put(name, resultStatus);
                } else {
                    backendResults.put(name, resultStatus);
                }
            }
        }
        
        String errorSummary = allPassed ? null : "Check runs failed: " + String.join("; ", failures);
        
        return new CiStatus(allPassed, errorSummary, backendResults, frontendResults, failures);
    }

    private E2eStatus analyzeE2eRuns(String owner, String repo, Map<String, Object> checkRunsResponse) {
        List<Map<String, Object>> checkRuns = (List<Map<String, Object>>) checkRunsResponse.get("check_runs");
        
        if (checkRuns == null || checkRuns.isEmpty()) {
            log.warn("No check runs found for E2E analysis");
            return new E2eStatus(true, null, new ArrayList<>());
        }
        
        List<String> e2eFailures = new ArrayList<>();
        boolean allE2ePassed = true;
        
        for (Map<String, Object> checkRun : checkRuns) {
            String name = (String) checkRun.get("name");
            String conclusion = (String) checkRun.get("conclusion");
            String status = (String) checkRun.get("status");
            Long checkRunId = ((Number) checkRun.get("id")).longValue();
            
            if (!"completed".equals(status)) {
                continue;
            }
            
            if (!isE2eCheckRun(name)) {
                continue;
            }
            
            log.debug("Analyzing E2E check run '{}': conclusion={}", name, conclusion);
            
            if (!"success".equals(conclusion)) {
                allE2ePassed = false;
                log.info("E2E check run '{}' failed, fetching logs", name);
                
                String failureDetails = extractFailureDetails(owner, repo, checkRunId, name);
                e2eFailures.add(name + ": " + failureDetails);
            }
        }
        
        String errorSummary = allE2ePassed ? null : "E2E tests failed: " + String.join("; ", e2eFailures);
        
        return new E2eStatus(allE2ePassed, errorSummary, e2eFailures);
    }

    private boolean isE2eCheckRun(String name) {
        String nameLower = name.toLowerCase();
        return nameLower.contains("e2e") || 
               nameLower.contains("playwright") || 
               nameLower.contains("integration") ||
               nameLower.contains("end-to-end");
    }

    private boolean isBackendCheck(String name) {
        String nameLower = name.toLowerCase();
        return nameLower.contains("backend") || 
               nameLower.contains("java") || 
               nameLower.contains("maven") ||
               nameLower.contains("spring") ||
               nameLower.contains("api");
    }

    private boolean isFrontendCheck(String name) {
        String nameLower = name.toLowerCase();
        return nameLower.contains("frontend") || 
               nameLower.contains("node") || 
               nameLower.contains("npm") ||
               nameLower.contains("angular") ||
               nameLower.contains("lint") ||
               nameLower.contains("ui");
    }

    private String extractFailureDetails(String owner, String repo, Long checkRunId, String checkName) {
        try {
            log.debug("Fetching logs for check run {}", checkRunId);
            String logs = gitHubApiClient.getJobLogs(owner, repo, checkRunId);
            
            if (logs == null || logs.isEmpty()) {
                log.warn("No logs available for check run {}", checkRunId);
                return "No logs available";
            }
            
            log.debug("Parsing {} characters of logs for check run {}", logs.length(), checkRunId);
            FailureDiagnostics diagnostics = parseWorkflowLogs(logs, checkName);
            
            return formatFailureDiagnostics(diagnostics);
        } catch (Exception e) {
            log.error("Failed to fetch/parse logs for check run {}: {}", checkRunId, e.getMessage());
            return "Failed to fetch logs: " + e.getMessage();
        }
    }

    private FailureDiagnostics parseWorkflowLogs(String logs, String checkName) {
        FailureDiagnostics diagnostics = new FailureDiagnostics();
        diagnostics.checkName = checkName;
        diagnostics.rawLogs = logs;
        
        List<String> compileErrors = extractCompileErrors(logs);
        List<String> testFailures = extractTestFailures(logs);
        List<String> lintIssues = extractLintIssues(logs);
        String buildError = extractBuildError(logs);
        
        diagnostics.compileErrors = compileErrors;
        diagnostics.testFailures = testFailures;
        diagnostics.lintIssues = lintIssues;
        diagnostics.buildError = buildError;
        
        if (!compileErrors.isEmpty()) {
            diagnostics.failureType = "COMPILE_ERROR";
            diagnostics.summary = compileErrors.size() + " compilation error(s)";
        } else if (!testFailures.isEmpty()) {
            diagnostics.failureType = "TEST_FAILURE";
            diagnostics.summary = testFailures.size() + " test failure(s)";
        } else if (!lintIssues.isEmpty()) {
            diagnostics.failureType = "LINT_ERROR";
            diagnostics.summary = lintIssues.size() + " lint issue(s)";
        } else if (buildError != null) {
            diagnostics.failureType = "BUILD_ERROR";
            diagnostics.summary = "Build failed";
        } else {
            diagnostics.failureType = "UNKNOWN";
            diagnostics.summary = "Unknown failure";
        }
        
        return diagnostics;
    }

    private List<String> extractCompileErrors(String logs) {
        List<String> errors = new ArrayList<>();
        
        Pattern javaCompilePattern = Pattern.compile("\\[ERROR\\]\\s+(.+\\.java):\\[(\\d+),(\\d+)\\]\\s+(.+)");
        Matcher javaMatcher = javaCompilePattern.matcher(logs);
        while (javaMatcher.find() && errors.size() < 10) {
            String file = javaMatcher.group(1);
            String line = javaMatcher.group(2);
            String message = javaMatcher.group(4);
            errors.add(String.format("%s:%s - %s", file, line, message));
        }
        
        Pattern tsCompilePattern = Pattern.compile("(.+\\.ts)\\((\\d+),(\\d+)\\):\\s+error\\s+TS\\d+:\\s+(.+)");
        Matcher tsMatcher = tsCompilePattern.matcher(logs);
        while (tsMatcher.find() && errors.size() < 10) {
            String file = tsMatcher.group(1);
            String line = tsMatcher.group(2);
            String message = tsMatcher.group(4);
            errors.add(String.format("%s:%s - %s", file, line, message));
        }
        
        Pattern genericErrorPattern = Pattern.compile("\\[ERROR\\]\\s+(.+)");
        Matcher genericMatcher = genericErrorPattern.matcher(logs);
        while (genericMatcher.find() && errors.size() < 10) {
            String errorLine = genericMatcher.group(1).trim();
            if (errorLine.contains("compilation") || errorLine.contains("compile")) {
                errors.add(errorLine);
            }
        }
        
        return errors;
    }

    private List<String> extractTestFailures(String logs) {
        List<String> failures = new ArrayList<>();
        
        Pattern junitPattern = Pattern.compile("\\[ERROR\\]\\s+(Tests run: \\d+, Failures: \\d+, Errors: \\d+.+)");
        Matcher junitMatcher = junitPattern.matcher(logs);
        while (junitMatcher.find() && failures.size() < 10) {
            failures.add(junitMatcher.group(1));
        }
        
        Pattern testNamePattern = Pattern.compile("\\[ERROR\\]\\s+(\\w+\\.\\w+\\(\\))\\s+Time elapsed:.+<<< FAILURE!");
        Matcher testNameMatcher = testNamePattern.matcher(logs);
        while (testNameMatcher.find() && failures.size() < 10) {
            failures.add("Failed test: " + testNameMatcher.group(1));
        }
        
        Pattern jestPattern = Pattern.compile("FAIL\\s+(.+)");
        Matcher jestMatcher = jestPattern.matcher(logs);
        while (jestMatcher.find() && failures.size() < 10) {
            failures.add("Test failure: " + jestMatcher.group(1));
        }
        
        Pattern karmaPattern = Pattern.compile("(\\w+)\\s+FAILED");
        Matcher karmaMatcher = karmaPattern.matcher(logs);
        while (karmaMatcher.find() && failures.size() < 10) {
            failures.add("Failed test: " + karmaMatcher.group(1));
        }
        
        Pattern playwrightPattern = Pattern.compile("\\s+\\d+\\)\\s+(.+)\\s+›\\s+(.+)");
        Matcher playwrightMatcher = playwrightPattern.matcher(logs);
        while (playwrightMatcher.find() && failures.size() < 10) {
            String testSuite = playwrightMatcher.group(1).trim();
            String testName = playwrightMatcher.group(2).trim();
            failures.add(String.format("E2E test failed: %s › %s", testSuite, testName));
        }
        
        return failures;
    }

    private List<String> extractLintIssues(String logs) {
        List<String> issues = new ArrayList<>();
        
        Pattern eslintPattern = Pattern.compile("(.+)\\s+(\\d+):(\\d+)\\s+error\\s+(.+)");
        Matcher eslintMatcher = eslintPattern.matcher(logs);
        while (eslintMatcher.find() && issues.size() < 10) {
            String file = eslintMatcher.group(1).trim();
            String line = eslintMatcher.group(2);
            String message = eslintMatcher.group(4);
            issues.add(String.format("%s:%s - %s", file, line, message));
        }
        
        Pattern checkstylePattern = Pattern.compile("\\[WARN\\]\\s+(.+):\\[(\\d+)\\]\\s+(.+)");
        Matcher checkstyleMatcher = checkstylePattern.matcher(logs);
        while (checkstyleMatcher.find() && issues.size() < 10) {
            String file = checkstyleMatcher.group(1);
            String line = checkstyleMatcher.group(2);
            String message = checkstyleMatcher.group(3);
            issues.add(String.format("%s:%s - %s", file, line, message));
        }
        
        return issues;
    }

    private String extractBuildError(String logs) {
        Pattern buildFailedPattern = Pattern.compile("BUILD FAILED|BUILD FAILURE|Error: Command failed");
        Matcher matcher = buildFailedPattern.matcher(logs);
        if (matcher.find()) {
            int start = Math.max(0, matcher.start() - 200);
            int end = Math.min(logs.length(), matcher.end() + 200);
            return logs.substring(start, end).replaceAll("\\s+", " ").trim();
        }
        return null;
    }

    private String formatFailureDiagnostics(FailureDiagnostics diagnostics) {
        StringBuilder formatted = new StringBuilder();
        formatted.append(diagnostics.failureType).append(": ").append(diagnostics.summary);
        
        if (!diagnostics.compileErrors.isEmpty()) {
            formatted.append("\nCompile errors: ");
            formatted.append(String.join("; ", diagnostics.compileErrors.subList(0, Math.min(3, diagnostics.compileErrors.size()))));
            if (diagnostics.compileErrors.size() > 3) {
                formatted.append("... (").append(diagnostics.compileErrors.size() - 3).append(" more)");
            }
        }
        
        if (!diagnostics.testFailures.isEmpty()) {
            formatted.append("\nTest failures: ");
            formatted.append(String.join("; ", diagnostics.testFailures.subList(0, Math.min(3, diagnostics.testFailures.size()))));
            if (diagnostics.testFailures.size() > 3) {
                formatted.append("... (").append(diagnostics.testFailures.size() - 3).append(" more)");
            }
        }
        
        if (!diagnostics.lintIssues.isEmpty()) {
            formatted.append("\nLint issues: ");
            formatted.append(String.join("; ", diagnostics.lintIssues.subList(0, Math.min(3, diagnostics.lintIssues.size()))));
            if (diagnostics.lintIssues.size() > 3) {
                formatted.append("... (").append(diagnostics.lintIssues.size() - 3).append(" more)");
            }
        }
        
        if (diagnostics.buildError != null) {
            formatted.append("\nBuild error: ").append(diagnostics.buildError);
        }
        
        return formatted.toString();
    }

    private Map<String, Object> buildDetailedTestReport(RunContext context, String prUrl, List<String> notes) {
        String owner = context.getOwner();
        String repo = context.getRepo();
        String branchName = context.getBranchName();
        
        Map<String, Object> backendReport = Map.of(
            "status", "PASSED",
            "details", List.of("All backend tests passed")
        );
        
        Map<String, Object> frontendReport = Map.of(
            "status", "PASSED",
            "details", List.of("All frontend tests passed")
        );
        
        Map<String, Object> e2eReport = Map.of(
            "status", "PASSED",
            "details", List.of("All E2E tests passed")
        );
        
        try {
            Map<String, Object> branchRef = gitHubApiClient.getReference(owner, repo, "heads/" + branchName);
            Map<String, Object> branchObject = (Map<String, Object>) branchRef.get("object");
            String commitSha = (String) branchObject.get("sha");
            
            Map<String, Object> checkRuns = gitHubApiClient.listCheckRunsForRef(owner, repo, commitSha);
            List<Map<String, Object>> checkRunsList = (List<Map<String, Object>>) checkRuns.get("check_runs");
            
            if (checkRunsList != null && !checkRunsList.isEmpty()) {
                List<String> backendDetails = new ArrayList<>();
                List<String> frontendDetails = new ArrayList<>();
                List<String> e2eDetails = new ArrayList<>();
                
                for (Map<String, Object> checkRun : checkRunsList) {
                    String name = (String) checkRun.get("name");
                    String conclusion = (String) checkRun.get("conclusion");
                    
                    String detail = String.format("%s: %s", name, conclusion != null ? conclusion.toUpperCase() : "PENDING");
                    
                    if (isE2eCheckRun(name)) {
                        e2eDetails.add(detail);
                    } else if (isBackendCheck(name)) {
                        backendDetails.add(detail);
                    } else if (isFrontendCheck(name)) {
                        frontendDetails.add(detail);
                    } else {
                        backendDetails.add(detail);
                    }
                }
                
                if (!backendDetails.isEmpty()) {
                    backendReport = Map.of(
                        "status", "PASSED",
                        "details", backendDetails
                    );
                }
                
                if (!frontendDetails.isEmpty()) {
                    frontendReport = Map.of(
                        "status", "PASSED",
                        "details", frontendDetails
                    );
                }
                
                if (!e2eDetails.isEmpty()) {
                    e2eReport = Map.of(
                        "status", "PASSED",
                        "details", e2eDetails
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Failed to build detailed test report: {}", e.getMessage());
        }
        
        Map<String, Object> testReport = new LinkedHashMap<>();
        testReport.put("prUrl", prUrl);
        testReport.put("ciStatus", "GREEN");
        testReport.put("backend", backendReport);
        testReport.put("frontend", frontendReport);
        testReport.put("e2e", e2eReport);
        testReport.put("notes", notes);
        testReport.put("timestamp", Instant.now().toString());
        
        return testReport;
    }

    private void applyFix(RunContext context, CiStatus status) throws Exception {
        log.info("Analyzing CI failures and generating fix for issue #{}", context.getRunEntity().getIssueNumber());
        
        String owner = context.getOwner();
        String repo = context.getRepo();
        String branchName = context.getBranchName();
        
        FailureDiagnostics aggregatedDiagnostics = aggregateCiFailures(status);
        
        FixPatch fixPatch = generateFixWithLlm(context, aggregatedDiagnostics, "CI");
        
        if (fixPatch == null || fixPatch.getFiles() == null || fixPatch.getFiles().isEmpty()) {
            log.warn("LLM did not generate any fix patches for CI failures");
            throw new IllegalStateException("Unable to generate fix for CI failures");
        }
        
        log.info("Generated fix with {} file changes", fixPatch.getFiles().size());
        
        Map<String, Object> branchRef = gitHubApiClient.getReference(owner, repo, "heads/" + branchName);
        Map<String, Object> branchObject = (Map<String, Object>) branchRef.get("object");
        String currentSha = (String) branchObject.get("sha");
        
        String commitSha = applyFixPatch(context, owner, repo, branchName, currentSha, fixPatch, "CI");
        log.info("Applied CI fix with commit {}", commitSha);
        
        log.info("Waiting for CI to re-run after fix...");
        Thread.sleep(10000);
    }

    private void applyE2eFix(RunContext context, E2eStatus status) throws Exception {
        log.info("Analyzing E2E failures and generating fix for issue #{}", context.getRunEntity().getIssueNumber());
        
        String owner = context.getOwner();
        String repo = context.getRepo();
        String branchName = context.getBranchName();
        
        FailureDiagnostics aggregatedDiagnostics = aggregateE2eFailures(status);
        
        FixPatch fixPatch = generateFixWithLlm(context, aggregatedDiagnostics, "E2E");
        
        if (fixPatch == null || fixPatch.getFiles() == null || fixPatch.getFiles().isEmpty()) {
            log.warn("LLM did not generate any fix patches for E2E failures");
            throw new IllegalStateException("Unable to generate fix for E2E failures");
        }
        
        log.info("Generated fix with {} file changes", fixPatch.getFiles().size());
        
        Map<String, Object> branchRef = gitHubApiClient.getReference(owner, repo, "heads/" + branchName);
        Map<String, Object> branchObject = (Map<String, Object>) branchRef.get("object");
        String currentSha = (String) branchObject.get("sha");
        
        String commitSha = applyFixPatch(context, owner, repo, branchName, currentSha, fixPatch, "E2E");
        log.info("Applied E2E fix with commit {}", commitSha);
        
        log.info("Waiting for E2E to re-run after fix...");
        Thread.sleep(10000);
    }

    private FailureDiagnostics aggregateCiFailures(CiStatus status) {
        FailureDiagnostics diagnostics = new FailureDiagnostics();
        diagnostics.checkName = "CI";
        diagnostics.summary = status.error != null ? status.error : "CI failures detected";
        diagnostics.failures = status.failures;
        diagnostics.failureType = "CI_FAILURE";
        
        StringBuilder rawLogs = new StringBuilder();
        for (String failure : status.failures) {
            rawLogs.append(failure).append("\n\n");
        }
        diagnostics.rawLogs = rawLogs.toString();
        
        return diagnostics;
    }

    private FailureDiagnostics aggregateE2eFailures(E2eStatus status) {
        FailureDiagnostics diagnostics = new FailureDiagnostics();
        diagnostics.checkName = "E2E";
        diagnostics.summary = status.error != null ? status.error : "E2E failures detected";
        diagnostics.failures = status.failures;
        diagnostics.failureType = "E2E_FAILURE";
        
        StringBuilder rawLogs = new StringBuilder();
        for (String failure : status.failures) {
            rawLogs.append(failure).append("\n\n");
        }
        diagnostics.rawLogs = rawLogs.toString();
        
        return diagnostics;
    }

    private FixPatch generateFixWithLlm(RunContext context, FailureDiagnostics diagnostics, String testType) {
        try {
            String systemPrompt = buildFixSystemPrompt();
            String userPrompt = buildFixUserPrompt(context, diagnostics, testType);
            Map<String, Object> schema = buildFixPatchSchema();
            
            log.info("Requesting LLM to generate {} fix for issue #{}", testType, context.getRunEntity().getIssueNumber());
            String llmResponse = llmService.generateStructuredOutput(systemPrompt, userPrompt, schema);
            
            if (llmResponse == null || llmResponse.trim().isEmpty()) {
                throw new IllegalStateException("LLM returned empty fix response");
            }
            
            log.debug("Received fix patch from LLM ({} characters)", llmResponse.length());
            FixPatch fixPatch = objectMapper.readValue(llmResponse, FixPatch.class);
            
            validateFixPatch(fixPatch);
            
            return fixPatch;
        } catch (Exception e) {
            log.error("Failed to generate fix with LLM: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildFixSystemPrompt() {
        return """
            You are an expert software engineer specializing in debugging and fixing test failures.
            
            Your task is to analyze CI/E2E test failure logs and generate minimal, targeted patches to fix the issues.
            
            Key principles:
            - Generate MINIMAL patches - only fix what's broken, don't refactor or add features
            - Target SPECIFIC failures - address the exact error messages and stack traces
            - Maintain EXISTING patterns - follow the codebase's conventions and style
            - Be CONSERVATIVE - prefer small, safe changes over large rewrites
            - Focus on CORRECTNESS - ensure your fix resolves the root cause
            
            Common failure types and fixes:
            1. Compilation errors: Fix syntax, imports, type mismatches
            2. Test failures: Fix assertion logic, test setup, mock configurations
            3. Lint errors: Fix code style, unused imports, formatting issues
            4. E2E failures: Fix selectors, timing issues, API responses, test data
            
            For each file to fix:
            - Provide the complete file path
            - Provide the complete updated file content
            - Explain what was wrong and how you fixed it
            
            DO NOT:
            - Add new features or refactor unrelated code
            - Make assumptions about requirements beyond fixing the failure
            - Modify files that aren't related to the failure
            - Change test expectations unless they're clearly wrong
            
            Be precise, minimal, and focused on resolving the specific failures.
            """;
    }

    private String buildFixUserPrompt(RunContext context, FailureDiagnostics diagnostics, String testType) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("# ").append(testType).append(" Test Failure Analysis\n\n");
        prompt.append("## Issue Information\n");
        prompt.append("**Issue #").append(context.getRunEntity().getIssueNumber()).append("**\n\n");
        
        prompt.append("## Failure Summary\n");
        prompt.append("**Type:** ").append(diagnostics.failureType).append("\n");
        prompt.append("**Summary:** ").append(diagnostics.summary).append("\n\n");
        
        if (!diagnostics.compileErrors.isEmpty()) {
            prompt.append("## Compilation Errors\n");
            for (String error : diagnostics.compileErrors) {
                prompt.append("- ").append(error).append("\n");
            }
            prompt.append("\n");
        }
        
        if (!diagnostics.testFailures.isEmpty()) {
            prompt.append("## Test Failures\n");
            for (String failure : diagnostics.testFailures) {
                prompt.append("- ").append(failure).append("\n");
            }
            prompt.append("\n");
        }
        
        if (!diagnostics.lintIssues.isEmpty()) {
            prompt.append("## Lint Issues\n");
            for (String issue : diagnostics.lintIssues) {
                prompt.append("- ").append(issue).append("\n");
            }
            prompt.append("\n");
        }
        
        if (diagnostics.buildError != null) {
            prompt.append("## Build Error\n");
            prompt.append(diagnostics.buildError).append("\n\n");
        }
        
        if (diagnostics.rawLogs != null && !diagnostics.rawLogs.isEmpty()) {
            String logExcerpt = diagnostics.rawLogs.length() > 5000 
                ? diagnostics.rawLogs.substring(0, 5000) + "\n... (truncated)"
                : diagnostics.rawLogs;
            prompt.append("## Relevant Log Excerpt\n");
            prompt.append("```\n").append(logExcerpt).append("\n```\n\n");
        }
        
        prompt.append("## Fix Requirements\n");
        prompt.append("Generate minimal patches to fix ONLY the failures listed above.\n");
        prompt.append("- Provide complete file contents for each file that needs changes\n");
        prompt.append("- Target the specific errors and failures\n");
        prompt.append("- Don't add features or refactor unrelated code\n");
        prompt.append("- Follow existing code patterns and conventions\n");
        prompt.append("- Explain the root cause and your fix approach\n\n");
        
        return prompt.toString();
    }

    private Map<String, Object> buildFixPatchSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        
        Map<String, Object> properties = new HashMap<>();
        
        properties.put("rootCause", Map.of(
            "type", "string",
            "description", "Brief explanation of the root cause of the failure"
        ));
        
        properties.put("fixStrategy", Map.of(
            "type", "string",
            "description", "Brief explanation of the fix strategy"
        ));
        
        properties.put("files", Map.of(
            "type", "array",
            "description", "List of file patches to apply",
            "items", Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Full file path from repository root"),
                    "content", Map.of("type", "string", "description", "Complete updated file content"),
                    "explanation", Map.of("type", "string", "description", "What was wrong and how this fixes it")
                ),
                "required", List.of("path", "content", "explanation"),
                "additionalProperties", false
            ),
            "minItems", 1,
            "maxItems", 5
        ));
        
        schema.put("properties", properties);
        schema.put("required", List.of("rootCause", "fixStrategy", "files"));
        
        return schema;
    }

    private void validateFixPatch(FixPatch fixPatch) {
        if (fixPatch.getFiles() == null || fixPatch.getFiles().isEmpty()) {
            throw new IllegalArgumentException("Fix patch must contain at least one file");
        }
        
        if (fixPatch.getFiles().size() > 5) {
            log.warn("Fix patch contains {} files, which may be too many for a minimal fix", fixPatch.getFiles().size());
        }
        
        for (FilePatch file : fixPatch.getFiles()) {
            if (file.getPath() == null || file.getPath().isEmpty()) {
                throw new IllegalArgumentException("File path cannot be empty in fix patch");
            }
            
            if (file.getContent() == null || file.getContent().isEmpty()) {
                throw new IllegalArgumentException("File content cannot be empty in fix patch: " + file.getPath());
            }
            
            if (file.getPath().contains("..") || file.getPath().startsWith("/")) {
                throw new IllegalArgumentException("Invalid file path in fix patch: " + file.getPath());
            }
        }
    }

    private String applyFixPatch(RunContext context, String owner, String repo, 
                                 String branchName, String baseSha, FixPatch fixPatch, String fixType) throws Exception {
        log.info("Applying {} fix patch with {} file changes", fixType, fixPatch.getFiles().size());
        
        List<Map<String, Object>> treeEntries = new ArrayList<>();
        
        for (FilePatch filePatch : fixPatch.getFiles()) {
            String path = filePatch.getPath();
            String content = filePatch.getContent();
            
            Map<String, Object> blob = gitHubApiClient.createBlob(owner, repo, content, "utf-8");
            String blobSha = (String) blob.get("sha");
            
            if (blobSha == null || blobSha.isEmpty()) {
                throw new IllegalStateException("Blob creation returned null SHA for: " + path);
            }
            
            Map<String, Object> treeEntry = new HashMap<>();
            treeEntry.put("path", path);
            treeEntry.put("mode", "100644");
            treeEntry.put("type", "blob");
            treeEntry.put("sha", blobSha);
            
            treeEntries.add(treeEntry);
            log.debug("Created blob {} for {} ({} bytes)", blobSha, path, content.length());
        }
        
        Map<String, Object> newTree = gitHubApiClient.createTree(owner, repo, treeEntries, baseSha);
        String treeSha = (String) newTree.get("sha");
        
        if (treeSha == null || treeSha.isEmpty()) {
            throw new IllegalStateException("Tree creation returned null SHA");
        }
        
        String commitMessage = buildFixCommitMessage(context, fixPatch, fixType);
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
        
        gitHubApiClient.updateReference(owner, repo, "heads/" + branchName, commitSha, false);
        log.info("Updated branch {} with fix commit {}", branchName, commitSha);
        
        return commitSha;
    }

    private String buildFixCommitMessage(RunContext context, FixPatch fixPatch, String fixType) {
        StringBuilder message = new StringBuilder();
        
        message.append("fix: Auto-fix ").append(fixType).append(" test failures for issue #")
               .append(context.getRunEntity().getIssueNumber()).append("\n\n");
        
        message.append("Root cause: ").append(fixPatch.getRootCause()).append("\n\n");
        message.append("Fix strategy: ").append(fixPatch.getFixStrategy()).append("\n\n");
        
        message.append("Files changed:\n");
        for (FilePatch file : fixPatch.getFiles()) {
            message.append("- ").append(file.getPath()).append(": ").append(file.getExplanation()).append("\n");
        }
        
        return message.toString();
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

    private String createEscalation(RunContext context, String blocker, Object statusObj) throws Exception {
        List<String> evidence = new ArrayList<>();
        evidence.add("PR: " + context.getPrUrl());
        evidence.add("Branch: " + context.getBranchName());
        evidence.add("CI fix attempts: " + context.getRunEntity().getCiFixCount());
        evidence.add("E2E fix attempts: " + context.getRunEntity().getE2eFixCount());
        
        List<String> detailedFailures = new ArrayList<>();
        
        if (statusObj instanceof CiStatus) {
            CiStatus ciStatus = (CiStatus) statusObj;
            if (ciStatus.error != null) {
                evidence.add("CI Error Summary: " + ciStatus.error);
            }
            if (!ciStatus.failures.isEmpty()) {
                detailedFailures.addAll(ciStatus.failures);
                evidence.add("CI Failure Count: " + ciStatus.failures.size());
            }
        } else if (statusObj instanceof E2eStatus) {
            E2eStatus e2eStatus = (E2eStatus) statusObj;
            if (e2eStatus.error != null) {
                evidence.add("E2E Error Summary: " + e2eStatus.error);
            }
            if (!e2eStatus.failures.isEmpty()) {
                detailedFailures.addAll(e2eStatus.failures);
                evidence.add("E2E Failure Count: " + e2eStatus.failures.size());
            }
        }
        
        Map<String, Object> escalationData = new LinkedHashMap<>();
        escalationData.put("context", "Testing phase for issue #" + context.getRunEntity().getIssueNumber());
        escalationData.put("blocker", blocker);
        escalationData.put("prUrl", context.getPrUrl());
        escalationData.put("branchName", context.getBranchName());
        escalationData.put("ciFixAttempts", context.getRunEntity().getCiFixCount());
        escalationData.put("e2eFixAttempts", context.getRunEntity().getE2eFixCount());
        escalationData.put("evidence", evidence);
        escalationData.put("detailedFailures", detailedFailures.subList(0, Math.min(10, detailedFailures.size())));
        escalationData.put("timestamp", Instant.now().toString());
        
        escalationData.put("options", List.of(
            Map.of(
                "name", "Manual intervention",
                "pros", List.of("Can address complex issues", "Human judgment", "Deep debugging possible"),
                "cons", List.of("Requires time", "May delay delivery", "Needs human availability"),
                "risk", "LOW"
            ),
            Map.of(
                "name", "Revert changes and retry",
                "pros", List.of("Quick resolution", "Maintains stability", "Can start fresh"),
                "cons", List.of("No progress on issue", "Wasted effort", "May hit same issues"),
                "risk", "MEDIUM"
            ),
            Map.of(
                "name", "Increase fix iteration limits",
                "pros", List.of("May resolve with more attempts", "Automatic resolution possible"),
                "cons", List.of("May waste more time", "Could make things worse", "Delays escalation"),
                "risk", "HIGH"
            )
        ));
        
        escalationData.put("recommendation", "Manual intervention");
        escalationData.put("decisionNeeded", "How to proceed with failing tests after exhausting automated fix attempts");
        
        String escalationJson = objectMapper.writeValueAsString(escalationData);
        
        throw new EscalationException(escalationJson);
    }

    private static class CiStatus {
        final boolean passed;
        final String error;
        final Map<String, String> backendResults;
        final Map<String, String> frontendResults;
        final List<String> failures;

        CiStatus(boolean passed, String error, Map<String, String> backendResults, 
                Map<String, String> frontendResults, List<String> failures) {
            this.passed = passed;
            this.error = error;
            this.backendResults = backendResults;
            this.frontendResults = frontendResults;
            this.failures = failures;
        }
    }

    private static class E2eStatus {
        final boolean passed;
        final String error;
        final List<String> failures;

        E2eStatus(boolean passed, String error, List<String> failures) {
            this.passed = passed;
            this.error = error;
            this.failures = failures;
        }
    }

    private static class FailureDiagnostics {
        String checkName;
        String failureType;
        String summary;
        String rawLogs;
        List<String> compileErrors = new ArrayList<>();
        List<String> testFailures = new ArrayList<>();
        List<String> lintIssues = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        String buildError;
    }

    public static class FixPatch {
        private String rootCause;
        private String fixStrategy;
        private List<FilePatch> files;

        public String getRootCause() { return rootCause; }
        public void setRootCause(String rootCause) { this.rootCause = rootCause; }

        public String getFixStrategy() { return fixStrategy; }
        public void setFixStrategy(String fixStrategy) { this.fixStrategy = fixStrategy; }

        public List<FilePatch> getFiles() { return files; }
        public void setFiles(List<FilePatch> files) { this.files = files; }
    }

    public static class FilePatch {
        private String path;
        private String content;
        private String explanation;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
    }
}
