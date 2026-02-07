package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.model.TicketPlan;
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
class PmStepTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private LlmService llmService;

    private ObjectMapper objectMapper;
    private PmStep pmStep;
    private RunContext context;
    private RunEntity runEntity;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        pmStep = new PmStep(gitHubApiClient, llmService, objectMapper);
        
        runEntity = new RunEntity(
            UUID.randomUUID(),
            "owner/repo",
            123,
            "full",
            RunStatus.RECEIVED,
            Instant.now()
        );
        
        context = new RunContext(runEntity, "owner", "repo");
    }

    @Test
    void execute_WithValidLlmResponse_GeneratesTicketPlan() throws Exception {
        Map<String, Object> issueData = createIssueData();
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());

        TicketPlan expectedPlan = new TicketPlan(
            123,
            "Test Issue",
            "Summary of the issue",
            List.of("Criterion 1", "Criterion 2"),
            List.of("Out of scope item"),
            List.of("Risk 1"),
            List.of("bug", "high-priority")
        );
        
        String llmResponse = objectMapper.writeValueAsString(expectedPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = pmStep.execute(context);

        assertNotNull(result);
        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertEquals(123, resultPlan.getIssueId());
        assertEquals("Test Issue", resultPlan.getTitle());
        assertNotNull(resultPlan.getAcceptanceCriteria());
        assertFalse(resultPlan.getAcceptanceCriteria().isEmpty());
        
        verify(gitHubApiClient).addLabelsToIssue(eq("owner"), eq("repo"), eq(123), anyList());
    }

    @Test
    void execute_WithLlmFailure_UsesFallbackStrategy() throws Exception {
        Map<String, Object> issueData = createIssueData();
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("LLM service unavailable"));

        String result = pmStep.execute(context);

        assertNotNull(result);
        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertEquals(123, resultPlan.getIssueId());
        assertEquals("Test Issue", resultPlan.getTitle());
        assertNotNull(resultPlan.getAcceptanceCriteria());
        assertFalse(resultPlan.getAcceptanceCriteria().isEmpty());
    }

    @Test
    void execute_WithComments_IncludesCommentsInPrompt() throws Exception {
        Map<String, Object> issueData = createIssueData();
        List<Map<String, Object>> comments = createComments();
        
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(comments);

        TicketPlan expectedPlan = new TicketPlan(
            123,
            "Test Issue",
            "Summary",
            List.of("Criterion 1"),
            List.of(),
            List.of(),
            List.of("bug")
        );
        
        String llmResponse = objectMapper.writeValueAsString(expectedPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        pmStep.execute(context);

        verify(llmService).generateStructuredOutput(
            anyString(),
            contains("Comments"),
            anyMap()
        );
    }

    @Test
    void execute_WithExplicitAcceptanceCriteria_ExtractsFromBody() throws Exception {
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Test Issue");
        issueData.put("body", """
            ## Acceptance Criteria
            - User can login successfully
            - User sees dashboard after login
            - Error message shown on invalid credentials
            """);
        issueData.put("labels", List.of());
        
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("Force fallback"));

        String result = pmStep.execute(context);

        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertTrue(resultPlan.getAcceptanceCriteria().size() >= 3);
        assertTrue(resultPlan.getAcceptanceCriteria().stream()
            .anyMatch(c -> c.contains("login")));
    }

    @Test
    void execute_WithOutOfScopeSection_ExtractsOutOfScope() throws Exception {
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Test Issue");
        issueData.put("body", """
            ## Out of Scope
            - Mobile app support
            - Email notifications
            """);
        issueData.put("labels", List.of());
        
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("Force fallback"));

        String result = pmStep.execute(context);

        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertFalse(resultPlan.getOutOfScope().isEmpty());
        assertTrue(resultPlan.getOutOfScope().stream()
            .anyMatch(item -> item.toLowerCase().contains("mobile") || 
                            item.toLowerCase().contains("email")));
    }

    @Test
    void execute_WithRisksInBody_ExtractsRisks() throws Exception {
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Test Issue");
        issueData.put("body", """
            This is a complex implementation that may require significant refactoring.
            Performance could be an issue with large datasets.
            Security implications require careful review.
            """);
        issueData.put("labels", List.of());
        
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("Force fallback"));

        String result = pmStep.execute(context);

        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertFalse(resultPlan.getRisks().isEmpty());
    }

    @Test
    void execute_WithBugKeywords_SuggestsBugLabel() throws Exception {
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Fix critical bug in login");
        issueData.put("body", "The application crashes when users try to login");
        issueData.put("labels", List.of());
        
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("Force fallback"));

        String result = pmStep.execute(context);

        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertTrue(resultPlan.getLabelsToApply().contains("bug"));
    }

    @Test
    void execute_WithEnhancementKeywords_SuggestsEnhancementLabel() throws Exception {
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Add new feature for user profiles");
        issueData.put("body", "Implement a new user profile page");
        issueData.put("labels", List.of());
        
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("Force fallback"));

        String result = pmStep.execute(context);

        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertTrue(resultPlan.getLabelsToApply().contains("enhancement"));
    }

    @Test
    void execute_WithLlmResponseMissingFields_EnhancesWithNlp() throws Exception {
        Map<String, Object> issueData = createIssueData();
        issueData.put("body", """
            ## Acceptance Criteria
            - System must handle 1000 concurrent users
            
            ## Risks
            - Database performance may be a bottleneck
            """);
        
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());

        TicketPlan incompletePlan = new TicketPlan(
            123,
            "Test Issue",
            "Summary",
            List.of(),
            null,
            List.of(),
            List.of("bug")
        );
        
        String llmResponse = objectMapper.writeValueAsString(incompletePlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = pmStep.execute(context);

        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertFalse(resultPlan.getAcceptanceCriteria().isEmpty());
        assertNotNull(resultPlan.getOutOfScope());
        assertFalse(resultPlan.getRisks().isEmpty());
    }

    @Test
    void execute_WithMarkdownCodeBlocks_CleansResponse() throws Exception {
        Map<String, Object> issueData = createIssueData();
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());

        TicketPlan expectedPlan = new TicketPlan(
            123,
            "Test Issue",
            "Summary",
            List.of("Criterion 1"),
            List.of(),
            List.of("Risk 1"),
            List.of("bug")
        );
        
        String llmResponse = "```json\n" + objectMapper.writeValueAsString(expectedPlan) + "\n```";
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = pmStep.execute(context);

        assertNotNull(result);
        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertEquals(123, resultPlan.getIssueId());
    }

    @Test
    void execute_WithCheckboxItems_ExtractsAsCriteria() throws Exception {
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Test Issue");
        issueData.put("body", """
            ## Tasks
            - [ ] Implement authentication
            - [x] Setup database
            - [ ] Write tests
            """);
        issueData.put("labels", List.of());
        
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("Force fallback"));

        String result = pmStep.execute(context);

        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertFalse(resultPlan.getAcceptanceCriteria().isEmpty());
    }

    @Test
    void execute_WithEmptyBody_GeneratesBasicPlan() throws Exception {
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Test Issue");
        issueData.put("body", "");
        issueData.put("labels", List.of());
        
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("Force fallback"));

        String result = pmStep.execute(context);

        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertEquals(123, resultPlan.getIssueId());
        assertEquals("Test Issue", resultPlan.getTitle());
        assertNotNull(resultPlan.getAcceptanceCriteria());
        assertFalse(resultPlan.getAcceptanceCriteria().isEmpty());
    }

    @Test
    void execute_WithLabelAdditionFailure_ContinuesExecution() throws Exception {
        Map<String, Object> issueData = createIssueData();
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());

        TicketPlan expectedPlan = new TicketPlan(
            123,
            "Test Issue",
            "Summary",
            List.of("Criterion 1"),
            List.of(),
            List.of("Risk 1"),
            List.of("bug")
        );
        
        String llmResponse = objectMapper.writeValueAsString(expectedPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);
        
        doThrow(new RuntimeException("GitHub API error"))
            .when(gitHubApiClient).addLabelsToIssue(anyString(), anyString(), anyInt(), anyList());

        String result = pmStep.execute(context);

        assertNotNull(result);
        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertEquals(123, resultPlan.getIssueId());
    }

    @Test
    void execute_WithNoLabelsInPlan_DoesNotCallAddLabels() throws Exception {
        Map<String, Object> issueData = createIssueData();
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());

        TicketPlan expectedPlan = new TicketPlan(
            123,
            "Test Issue",
            "Summary",
            List.of("Criterion 1"),
            List.of(),
            List.of("Risk 1"),
            List.of()
        );
        
        String llmResponse = objectMapper.writeValueAsString(expectedPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        pmStep.execute(context);

        verify(gitHubApiClient, never()).addLabelsToIssue(anyString(), anyString(), anyInt(), anyList());
    }

    @Test
    void execute_WithGivenWhenThenFormat_ExtractsAsCriteria() throws Exception {
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Test Issue");
        issueData.put("body", """
            Given a user is logged in
            When they navigate to the profile page
            Then they should see their profile information
            """);
        issueData.put("labels", List.of());
        
        when(gitHubApiClient.readIssue("owner", "repo", 123)).thenReturn(issueData);
        when(gitHubApiClient.listIssueComments("owner", "repo", 123)).thenReturn(List.of());
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("Force fallback"));

        String result = pmStep.execute(context);

        TicketPlan resultPlan = objectMapper.readValue(result, TicketPlan.class);
        assertTrue(resultPlan.getAcceptanceCriteria().stream()
            .anyMatch(c -> c.contains("Given") || c.contains("When") || c.contains("Then")));
    }

    private Map<String, Object> createIssueData() {
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Test Issue");
        issueData.put("body", "This is a test issue body with some requirements.");
        
        List<Map<String, String>> labels = new ArrayList<>();
        Map<String, String> label = new HashMap<>();
        label.put("name", "existing-label");
        labels.add(label);
        issueData.put("labels", labels);
        
        return issueData;
    }

    private List<Map<String, Object>> createComments() {
        List<Map<String, Object>> comments = new ArrayList<>();
        
        Map<String, Object> comment1 = new HashMap<>();
        comment1.put("body", "This is an important clarification about the requirements");
        Map<String, Object> user1 = new HashMap<>();
        user1.put("login", "user1");
        comment1.put("user", user1);
        comments.add(comment1);
        
        Map<String, Object> comment2 = new HashMap<>();
        comment2.put("body", "We should also consider edge cases");
        Map<String, Object> user2 = new HashMap<>();
        user2.put("login", "user2");
        comment2.put("user", user2);
        comments.add(comment2);
        
        return comments;
    }
}
