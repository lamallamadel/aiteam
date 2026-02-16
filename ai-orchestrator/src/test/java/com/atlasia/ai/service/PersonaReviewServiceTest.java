package com.atlasia.ai.service;

import com.atlasia.ai.config.PersonaConfig;
import com.atlasia.ai.config.PersonaConfigLoader;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonaReviewServiceTest {

    @Mock
    private PersonaConfigLoader personaConfigLoader;

    @Mock
    private LlmService llmService;

    private ObjectMapper objectMapper;
    private PersonaReviewService personaReviewService;
    private RunContext context;
    private DeveloperStep.CodeChanges codeChanges;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        personaReviewService = new PersonaReviewService(personaConfigLoader, llmService, objectMapper);

        RunEntity runEntity = new RunEntity(
                UUID.randomUUID(),
                "owner/repo",
                123,
                "full",
                RunStatus.DEVELOPER,
                Instant.now());

        context = new RunContext(runEntity, "owner", "repo");
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Add authentication");
        issueData.put("body", "Implement user authentication");
        context.setIssueData(issueData);

        codeChanges = new DeveloperStep.CodeChanges();
        codeChanges.setSummary("Added authentication service");
        codeChanges.setTestingNotes("Unit tests included");
        codeChanges.setImplementationNotes("Follows security best practices");

        List<DeveloperStep.FileChange> files = new ArrayList<>();
        DeveloperStep.FileChange file1 = new DeveloperStep.FileChange();
        file1.setPath("src/main/java/AuthService.java");
        file1.setOperation("create");
        file1.setContent("public class AuthService { /* implementation */ }");
        file1.setExplanation("Authentication service");
        files.add(file1);

        codeChanges.setFiles(files);
    }

    @Test
    void testReviewCodeChanges_withSecurityRejection() throws Exception {
        PersonaConfig securityEngineerConfig = createSecurityEngineerPersonaConfig();
        when(personaConfigLoader.getPersonas()).thenReturn(List.of(securityEngineerConfig));

        String llmResponse = """
                {
                    "overallAssessment": "Critical security vulnerability detected",
                    "issues": [
                        {
                            "severity": "critical",
                            "filePath": "src/main/java/AuthService.java",
                            "description": "Missing input validation on authentication endpoint",
                            "recommendation": "Add @Valid annotation and implement proper input sanitization",
                            "mandatory": true
                        }
                    ],
                    "enhancements": [
                        {
                            "category": "validation",
                            "description": "Implement comprehensive input validation",
                            "benefit": "Prevents injection attacks and malformed data"
                        }
                    ]
                }
                """;

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap())).thenReturn(llmResponse);

        PersonaReviewService.PersonaReviewReport report = personaReviewService.reviewCodeChanges(context, codeChanges);

        assertNotNull(report);
        assertEquals(1, report.getFindings().size());
        assertTrue(report.isSecurityFixesApplied());

        PersonaReviewService.PersonaReview securityEngineerReview = report.getFindings().get(0);
        assertEquals("security-engineer", securityEngineerReview.getPersonaName());
        assertEquals(1, securityEngineerReview.getIssues().size());

        PersonaReviewService.PersonaIssue issue = securityEngineerReview.getIssues().get(0);
        assertEquals("critical", issue.getSeverity());
        assertTrue(issue.isMandatory());

        verify(llmService, times(1)).generateStructuredOutput(anyString(), anyString(), anyMap());
    }

    @Test
    void testReviewCodeChanges_withInfraRecommendation() throws Exception {
        PersonaConfig sreEngineerConfig = createSreEngineerPersonaConfig();
        when(personaConfigLoader.getPersonas()).thenReturn(List.of(sreEngineerConfig));

        String llmResponse = """
                {
                    "overallAssessment": "Infrastructure configuration looks reasonable with minor improvements needed",
                    "issues": [
                        {
                            "severity": "medium",
                            "filePath": "src/main/java/AuthService.java",
                            "description": "Consider caching strategy for authentication tokens",
                            "recommendation": "Implement Redis cache for token validation",
                            "mandatory": false
                        }
                    ],
                    "enhancements": [
                        {
                            "category": "caching",
                            "description": "Add distributed caching for improved performance",
                            "benefit": "Reduces database load and improves response times"
                        },
                        {
                            "category": "scalability",
                            "description": "Ensure authentication service can scale horizontally",
                            "benefit": "Handles increased load without performance degradation"
                        }
                    ]
                }
                """;

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap())).thenReturn(llmResponse);

        PersonaReviewService.PersonaReviewReport report = personaReviewService.reviewCodeChanges(context, codeChanges);

        assertNotNull(report);
        assertEquals(1, report.getFindings().size());
        assertFalse(report.isSecurityFixesApplied());

        PersonaReviewService.PersonaReview sreEngineerReview = report.getFindings().get(0);
        assertEquals("sre-engineer", sreEngineerReview.getPersonaName());
        assertEquals(1, sreEngineerReview.getIssues().size());
        assertEquals(2, sreEngineerReview.getEnhancements().size());

        PersonaReviewService.PersonaIssue issue = sreEngineerReview.getIssues().get(0);
        assertEquals("medium", issue.getSeverity());
        assertFalse(issue.isMandatory());

        assertTrue(report.getMergedRecommendations().size() > 0);
        verify(llmService, times(1)).generateStructuredOutput(anyString(), anyString(), anyMap());
    }

    @Test
    void testReviewCodeChanges_withFrontendEnhancement() throws Exception {
        PersonaConfig frontendUxEngineerConfig = createFrontendUxEngineerPersonaConfig();
        when(personaConfigLoader.getPersonas()).thenReturn(List.of(frontendUxEngineerConfig));

        String llmResponse = """
                {
                    "overallAssessment": "Good implementation but missing progress feedback",
                    "issues": [
                        {
                            "severity": "high",
                            "filePath": "src/main/java/AuthService.java",
                            "description": "No progress feedback for authentication process",
                            "recommendation": "Add loading state and progress indicators for login flow",
                            "mandatory": false
                        }
                    ],
                    "enhancements": [
                        {
                            "category": "progress_feedback",
                            "description": "Add clear loading states during authentication",
                            "benefit": "Users understand system is processing their request"
                        },
                        {
                            "category": "accessibility",
                            "description": "Ensure authentication forms meet WCAG 2.1 AA standards",
                            "benefit": "Makes authentication accessible to all users"
                        },
                        {
                            "category": "error_handling",
                            "description": "Provide specific error messages for failed authentication",
                            "benefit": "Helps users understand and resolve authentication issues"
                        }
                    ]
                }
                """;

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap())).thenReturn(llmResponse);

        PersonaReviewService.PersonaReviewReport report = personaReviewService.reviewCodeChanges(context, codeChanges);

        assertNotNull(report);
        assertEquals(1, report.getFindings().size());
        assertFalse(report.isSecurityFixesApplied());

        PersonaReviewService.PersonaReview frontendUxEngineerReview = report.getFindings().get(0);
        assertEquals("frontend-ux-engineer", frontendUxEngineerReview.getPersonaName());
        assertEquals("Frontend Experience Guardian", frontendUxEngineerReview.getPersonaRole());
        assertEquals(1, frontendUxEngineerReview.getIssues().size());
        assertEquals(3, frontendUxEngineerReview.getEnhancements().size());

        assertTrue(report.getMergedRecommendations().stream()
                .anyMatch(r -> r.contains("progress_feedback")));
        assertTrue(report.getMergedRecommendations().stream()
                .anyMatch(r -> r.contains("accessibility")));

        verify(llmService, times(1)).generateStructuredOutput(anyString(), anyString(), anyMap());
    }

    @Test
    void testReviewCodeChanges_withMultiplePersonas() throws Exception {
        PersonaConfig securityEngineerConfig = createSecurityEngineerPersonaConfig();
        PersonaConfig codeQualityEngineerConfig = createCodeQualityEngineerPersonaConfig();

        when(personaConfigLoader.getPersonas()).thenReturn(List.of(securityEngineerConfig, codeQualityEngineerConfig));

        String securityEngineerResponse = """
                {
                    "overallAssessment": "Security looks good",
                    "issues": [],
                    "enhancements": []
                }
                """;

        String codeQualityEngineerResponse = """
                {
                    "overallAssessment": "Code quality is acceptable",
                    "issues": [
                        {
                            "severity": "low",
                            "filePath": "src/main/java/AuthService.java",
                            "description": "Consider extracting validation logic to separate class",
                            "recommendation": "Refactor to follow single responsibility principle",
                            "mandatory": false
                        }
                    ],
                    "enhancements": [
                        {
                            "category": "refactoring",
                            "description": "Extract validation logic",
                            "benefit": "Improves testability and maintainability"
                        }
                    ]
                }
                """;

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(securityEngineerResponse, codeQualityEngineerResponse);

        PersonaReviewService.PersonaReviewReport report = personaReviewService.reviewCodeChanges(context, codeChanges);

        assertNotNull(report);
        assertEquals(2, report.getFindings().size());
        assertFalse(report.isSecurityFixesApplied());

        verify(llmService, times(2)).generateStructuredOutput(anyString(), anyString(), anyMap());
    }

    @Test
    void testReviewCodeChanges_withNoPersonas() {
        when(personaConfigLoader.getPersonas()).thenReturn(List.of());

        PersonaReviewService.PersonaReviewReport report = personaReviewService.reviewCodeChanges(context, codeChanges);

        assertNotNull(report);
        assertEquals(0, report.getFindings().size());
        assertFalse(report.isSecurityFixesApplied());

        verify(llmService, never()).generateStructuredOutput(anyString(), anyString(), anyMap());
    }

    @Test
    void testReviewCodeChanges_withLlmFailure() throws Exception {
        PersonaConfig securityEngineerConfig = createSecurityEngineerPersonaConfig();
        when(personaConfigLoader.getPersonas()).thenReturn(List.of(securityEngineerConfig));
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("LLM service unavailable"));

        PersonaReviewService.PersonaReviewReport report = personaReviewService.reviewCodeChanges(context, codeChanges);

        assertNotNull(report);
        assertEquals(1, report.getFindings().size());

        PersonaReviewService.PersonaReview failedReview = report.getFindings().get(0);
        assertTrue(failedReview.getOverallAssessment().contains("error"));
        assertEquals(0, failedReview.getIssues().size());
    }

    private PersonaConfig createSecurityEngineerPersonaConfig() {
        return new PersonaConfig(
                "security-engineer",
                "Security Specialist",
                "Identify security vulnerabilities",
                List.of("Input validation", "SQL injection", "XSS prevention"),
                List.of("Are secrets protected?", "Is input validated?"),
                Map.of(
                        "critical", List.of("Hardcoded secrets"),
                        "high", List.of("Missing input validation")),
                List.of(
                        new PersonaConfig.Enhancement("validation", "All inputs must be validated"),
                        new PersonaConfig.Enhancement("sanitization", "File uploads must be validated")));
    }

    private PersonaConfig createSreEngineerPersonaConfig() {
        return new PersonaConfig(
                "sre-engineer",
                "Infrastructure Architect",
                "Validate infrastructure decisions",
                List.of("Caching strategy", "Scalability", "Performance"),
                List.of("Is caching implemented?", "Can it scale horizontally?"),
                Map.of(
                        "critical", List.of("No resource limits"),
                        "medium", List.of("Suboptimal caching")),
                List.of(
                        new PersonaConfig.Enhancement("caching", "Implement caching where appropriate"),
                        new PersonaConfig.Enhancement("scalability", "Architecture must support horizontal scaling")));
    }

    private PersonaConfig createFrontendUxEngineerPersonaConfig() {
        return new PersonaConfig(
                "frontend-ux-engineer",
                "Frontend Experience Guardian",
                "Ensure excellent user experience",
                List.of("Progress feedback", "Accessibility", "Error messaging"),
                List.of("Is progress feedback provided?", "Are accessibility standards met?"),
                Map.of(
                        "critical", List.of("No progress feedback"),
                        "high", List.of("Missing loading states")),
                List.of(
                        new PersonaConfig.Enhancement("progress_feedback", "Show clear progress indicators"),
                        new PersonaConfig.Enhancement("accessibility", "Meet WCAG 2.1 AA standards")));
    }

    private PersonaConfig createCodeQualityEngineerPersonaConfig() {
        return new PersonaConfig(
                "code-quality-engineer",
                "Code Quality Guardian",
                "Ensure code maintainability",
                List.of("Error handling", "Test coverage", "Code duplication"),
                List.of("Are exceptions handled?", "Is test coverage adequate?"),
                Map.of(
                        "critical", List.of("No error handling"),
                        "medium", List.of("Code duplication")),
                List.of(
                        new PersonaConfig.Enhancement("error_handling", "Handle all error conditions"),
                        new PersonaConfig.Enhancement("testing", "Achieve minimum coverage thresholds")));
    }
}
