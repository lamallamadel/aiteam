package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.event.WorkflowEventBus;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import com.atlasia.ai.service.trace.TraceEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

        @Mock
        private RunRepository runRepository;

        @Mock
        private JsonSchemaValidator schemaValidator;

        @Mock
        private PmStep pmStep;

        @Mock
        private QualifierStep qualifierStep;

        @Mock
        private ArchitectStep architectStep;

        @Mock
        private DeveloperStep developerStep;

        @Mock
        private PersonaReviewService personaReviewService;

        @Mock
        private TesterStep testerStep;

        @Mock
        private WriterStep writerStep;

        @Mock
        private OrchestratorMetrics metrics;

        @Mock
        private WorkflowEventBus eventBus;

        @Mock
        private TraceEventService traceEventService;

        @Mock
        private BlackboardService blackboardService;

        @Mock
        private DynamicInterruptService interruptService;

        @Mock
        private JudgeService judgeService;

        @Mock
        private AgentStepFactory agentStepFactory;

        @Mock
        private AgentBindingService agentBindingService;

        @Mock
        private GraftExecutionService graftExecutionService;

        @Mock
        private io.opentelemetry.api.trace.Tracer tracer;

        private WorkflowEngine workflowEngine;
        private RunEntity runEntity;

        @BeforeEach
        void setUp() {
                io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
                io.opentelemetry.api.trace.SpanBuilder mockBuilder = mock(io.opentelemetry.api.trace.SpanBuilder.class);
                lenient().when(tracer.spanBuilder(any())).thenReturn(mockBuilder);
                lenient().when(mockBuilder.setAttribute(any(String.class), any(String.class))).thenReturn(mockBuilder);
                lenient().when(mockBuilder.setAttribute(any(String.class), anyInt())).thenReturn(mockBuilder);
                lenient().when(mockBuilder.startSpan()).thenReturn(mockSpan);
                lenient().when(mockSpan.makeCurrent()).thenReturn(mock(io.opentelemetry.context.Scope.class));
                
                workflowEngine = new WorkflowEngine(
                                runRepository,
                                schemaValidator,
                                developerStep,
                                personaReviewService,
                                metrics,
                                eventBus,
                                traceEventService,
                                blackboardService,
                                interruptService,
                                judgeService,
                                agentStepFactory,
                                agentBindingService,
                                new com.atlasia.ai.service.InterruptDecisionStore(),
                                new ObjectMapper(),
                                graftExecutionService,
                                tracer);
                ReflectionTestUtils.setField(workflowEngine, "self", workflowEngine);

                // Wire the factory to return the appropriate step mocks
                lenient().when(agentStepFactory.resolveForRole(eq("PM"), any(Set.class))).thenReturn(pmStep);
                lenient().when(agentStepFactory.resolveForRole(eq("QUALIFIER"), any(Set.class))).thenReturn(qualifierStep);
                lenient().when(agentStepFactory.resolveForRole(eq("ARCHITECT"), any(Set.class))).thenReturn(architectStep);
                lenient().when(agentStepFactory.resolveForRole(eq("TESTER"), any(Set.class))).thenReturn(testerStep);
                lenient().when(agentStepFactory.resolveForRole(eq("WRITER"), any(Set.class))).thenReturn(writerStep);

                runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());
                lenient().when(runRepository.findById(any())).thenReturn(Optional.of(runEntity));
                lenient().when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                Timer.Sample mockSample = mock(Timer.Sample.class);
                Timer mockTimer = mock(Timer.class);
                lenient().when(metrics.startWorkflowTimer()).thenReturn(mockSample);
                lenient().when(metrics.startAgentStepTimer()).thenReturn(mockSample);
                lenient().when(metrics.getWorkflowDuration()).thenReturn(mockTimer);
                lenient().when(metrics.getAgentStepDuration()).thenReturn(mockTimer);

                setupPersonaReviewMock();
                setupJudgeMock();
                setupInterruptMock();
        }

        private void setupJudgeMock() {
                JudgeService.JudgeVerdict passingVerdict = new JudgeService.JudgeVerdict(
                                UUID.randomUUID(), "pre_merge", "persona_review_report", "code_quality",
                                0.85, "pass", 0.9, Map.of(),
                                List.of(), "Artifact meets quality bar. Proceed to next step.",
                                null, Instant.now());
                lenient().when(judgeService.evaluate(any(), anyString(), anyString())).thenReturn(passingVerdict);
                lenient().when(judgeService.evaluateWithMajorityVoting(any(), anyString(), anyString()))
                                .thenReturn(passingVerdict);
        }

        private void setupInterruptMock() {
                DynamicInterruptService.InterruptDecision proceed = DynamicInterruptService.InterruptDecision.proceed();
                lenient().when(interruptService.evaluate(anyString(), any(), anyString(), any(), any()))
                                .thenReturn(proceed);
        }

        private void setupPersonaReviewMock() {
                PersonaReviewService.PersonaReviewReport report = mock(PersonaReviewService.PersonaReviewReport.class);
                lenient().when(report.getFindings()).thenReturn(java.util.List.of());
                lenient().when(report.getMergedRecommendations()).thenReturn(java.util.List.of());
                lenient().when(personaReviewService.reviewCodeChanges(any(), any())).thenReturn(report);
        }

        private void setupDeveloperStepMocks() throws Exception {
                doAnswer(invocation -> {
                        RunContext ctx = invocation.getArgument(0);
                        DeveloperStep.CodeChanges codeChanges = new DeveloperStep.CodeChanges();
                        codeChanges.setSummary("Implemented feature");
                        codeChanges.setFiles(java.util.List.of());
                        ctx.setCodeChanges(codeChanges);
                        return null;
                }).when(developerStep).generateCode(any(RunContext.class));

                lenient().when(developerStep.commitAndCreatePullRequest(any(), any()))
                                .thenReturn("https://github.com/owner/repo/pull/1");
        }

        @Test
        void executeWorkflow_successfulFullWorkflow_completesAllSteps() throws Exception {
                lenient().when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                lenient().when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                lenient().when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                setupDeveloperStepMocks();
                lenient().when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                lenient().when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(pmStep).execute(any(RunContext.class));
                verify(qualifierStep).execute(any(RunContext.class));
                verify(architectStep).execute(any(RunContext.class));
                verify(developerStep).generateCode(any(RunContext.class));
                verify(personaReviewService).reviewCodeChanges(any(), any());
                verify(developerStep).commitAndCreatePullRequest(any(), any());
                verify(testerStep).execute(any(RunContext.class));
                verify(writerStep).execute(any(RunContext.class));

                verify(schemaValidator).validate(anyString(), eq("ticket_plan.schema.json"));
                verify(schemaValidator).validate(anyString(), eq("work_plan.schema.json"));
                verify(schemaValidator).validate(anyString(), eq("test_report.schema.json"));

                verify(runRepository, atLeastOnce()).save(
                                argThat(run -> run.getStatus() == RunStatus.DONE && run.getCurrentAgent() == null));

                verify(metrics).recordWorkflowExecution();
                verify(metrics).recordWorkflowSuccess(anyLong());
        }

        @Test
        void executeWorkflow_pmStepFailure_setsFailedStatus() throws Exception {
                lenient().when(pmStep.execute(any(RunContext.class)))
                                .thenThrow(new RuntimeException("PM step failed"));

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(pmStep).execute(any(RunContext.class));
                verify(qualifierStep, never()).execute(any(RunContext.class));

                verify(runRepository, atLeastOnce()).save(argThat(run -> run.getStatus() == RunStatus.FAILED));
                verify(metrics).recordWorkflowFailure(anyLong());
        }

        @Test
        void executeWorkflow_escalationException_setsEscalatedStatus() throws Exception {
                String escalationJson = "{\"context\":\"test\",\"blocker\":\"test\"}";
                lenient().when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                lenient().when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                lenient().when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                setupDeveloperStepMocks();
                lenient().when(testerStep.execute(any(RunContext.class)))
                                .thenThrow(new EscalationException(escalationJson));

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(testerStep).execute(any(RunContext.class));
                verify(writerStep, never()).execute(any(RunContext.class));

                verify(schemaValidator).validate(eq(escalationJson), eq("escalation.schema.json"));
                verify(runRepository, atLeastOnce()).save(argThat(run -> run.getStatus() == RunStatus.ESCALATED));
                verify(metrics).recordWorkflowEscalation(anyLong());
        }

        @Test
        void executeWorkflow_orchestratorException_handlesGracefully() throws Exception {
                lenient().when(pmStep.execute(any(RunContext.class)))
                                .thenThrow(new com.atlasia.ai.service.exception.WorkflowException(
                                                "Service unavailable",
                                                UUID.randomUUID(),
                                                "PM",
                                                com.atlasia.ai.service.exception.OrchestratorException.RecoveryStrategy.RETRY_WITH_BACKOFF));

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(runRepository, atLeastOnce()).save(argThat(run -> run.getStatus() == RunStatus.FAILED));
                verify(metrics).recordWorkflowFailure(anyLong());
        }

        @Test
        void executeWorkflow_storesArtifactsFromEachStep() throws Exception {
                lenient().when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                lenient().when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                lenient().when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                setupDeveloperStepMocks();
                lenient().when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                lenient().when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflow(runEntity.getId());

                assertTrue(runEntity.getArtifacts().size() >= 6);

                assertTrue(runEntity.getArtifacts().stream()
                                .anyMatch(a -> "PM".equals(a.getAgentName())
                                                && "ticket_plan.json".equals(a.getArtifactType())));
                assertTrue(runEntity.getArtifacts().stream()
                                .anyMatch(a -> "QUALIFIER".equals(a.getAgentName())
                                                && "work_plan.json".equals(a.getArtifactType())));
                assertTrue(runEntity.getArtifacts().stream()
                                .anyMatch(a -> "ARCHITECT".equals(a.getAgentName())
                                                && "architecture_notes.md".equals(a.getArtifactType())));
                assertTrue(runEntity.getArtifacts().stream()
                                .anyMatch(a -> "DEVELOPER".equals(a.getAgentName())
                                                && "pr_url".equals(a.getArtifactType())));
                assertTrue(runEntity.getArtifacts().stream()
                                .anyMatch(a -> "TESTER".equals(a.getAgentName())
                                                && "test_report.json".equals(a.getArtifactType())));
                assertTrue(runEntity.getArtifacts().stream()
                                .anyMatch(a -> "WRITER".equals(a.getAgentName())
                                                && "docs_update".equals(a.getArtifactType())));
        }

        @Test
        void executeWorkflow_updatesCurrentAgentThroughoutExecution() throws Exception {
                // Setup mocks
                lenient().when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                lenient().when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                lenient().when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                setupDeveloperStepMocks();
                lenient().when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                lenient().when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                // Capture history using doAnswer because RunEntity is mutable
                List<String> statusHistory = new ArrayList<>();
                lenient().doAnswer(invocation -> {
                        RunEntity run = invocation.getArgument(0);
                        // Store immutable snapshot of state
                        statusHistory.add(run.getStatus() + ":" + run.getCurrentAgent());
                        return run;
                }).when(runRepository).save(any(RunEntity.class));

                workflowEngine.executeWorkflow(runEntity.getId());

                // Verify the sequence of states
                // Note: The order and existence of these states matters
                assertTrue(statusHistory.contains("PM:PM"), "Should save PM state");
                assertTrue(statusHistory.contains("QUALIFIER:QUALIFIER"), "Should save QUALIFIER state");
                assertTrue(statusHistory.contains("ARCHITECT:ARCHITECT"), "Should save ARCHITECT state");
                assertTrue(statusHistory.contains("DEVELOPER:DEVELOPER"), "Should save DEVELOPER state");
                assertTrue(statusHistory.contains("REVIEW:REVIEW"), "Should save REVIEW state");
                assertTrue(statusHistory.contains("TESTER:TESTER"), "Should save TESTER state");
                assertTrue(statusHistory.contains("WRITER:WRITER"), "Should save WRITER state");

                // Final state
                assertTrue(statusHistory.stream().anyMatch(s -> s.startsWith("DONE:null")),
                                "Should end in DONE state with null agent");
        }

        @Test
        void executeWorkflow_schemaValidationFailure_failsWorkflow() throws Exception {
                lenient().when(pmStep.execute(any(RunContext.class))).thenReturn("{\"invalid\":\"json\"}");
                lenient().doThrow(new IllegalArgumentException("Schema validation failed"))
                                .when(schemaValidator).validate(anyString(), eq("ticket_plan.schema.json"));

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(runRepository, atLeastOnce()).save(argThat(run -> run.getStatus() == RunStatus.FAILED));
                verify(qualifierStep, never()).execute(any(RunContext.class));
                verify(metrics).recordWorkflowFailure(anyLong());
        }

        @Test
        void executeWorkflowAsync_loadsRunAndExecutes() throws Exception {
                UUID runId = runEntity.getId();
                lenient().when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                lenient().when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                lenient().when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                setupDeveloperStepMocks();
                lenient().when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                lenient().when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflowAsync(runId, "test-token");

                Thread.sleep(200);

                verify(runRepository, atLeastOnce()).findById(runId);
                verify(pmStep, atLeastOnce()).execute(any(RunContext.class));
                verify(metrics, atLeastOnce()).recordWorkflowExecution();
        }

        @Test
        void executeWorkflowAsync_runNotFound_throwsWorkflowException() {
                UUID runId = UUID.randomUUID();
                lenient().when(runRepository.findById(runId)).thenReturn(Optional.empty());

                assertThrows(com.atlasia.ai.service.exception.WorkflowException.class,
                                () -> workflowEngine.executeWorkflowAsync(runId, "test-token"));
        }

        @Test
        void executeWorkflow_parsesRepoFromRunEntity() throws Exception {
                runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "testowner/testrepo",
                                456,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());
                // Override the default mock for this specific test
                lenient().when(runRepository.findById(any())).thenReturn(Optional.of(runEntity));

                lenient().when(pmStep.execute(any(RunContext.class))).thenAnswer(invocation -> {
                        RunContext context = invocation.getArgument(0);
                        assertEquals("testowner", context.getOwner());
                        assertEquals("testrepo", context.getRepo());
                        return "{\"issueId\":456}";
                });

                lenient().when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                lenient().when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                setupDeveloperStepMocks();
                lenient().when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                lenient().when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(pmStep).execute(
                                argThat(ctx -> "testowner".equals(ctx.getOwner()) && "testrepo".equals(ctx.getRepo())));
        }

        @Test
        void executeWorkflow_storesErrorArtifactOnFailure() throws Exception {
                lenient().when(pmStep.execute(any(RunContext.class)))
                                .thenThrow(new RuntimeException("Test error message"));

                workflowEngine.executeWorkflow(runEntity.getId());

                assertTrue(runEntity.getArtifacts().stream()
                                .anyMatch(a -> "error_details".equals(a.getArtifactType())));

                String errorArtifact = runEntity.getArtifacts().stream()
                                .filter(a -> "error_details".equals(a.getArtifactType()))
                                .findFirst()
                                .map(com.atlasia.ai.model.RunArtifactEntity::getPayload)
                                .orElse("");

                assertTrue(errorArtifact.contains("RuntimeException"));
                assertTrue(errorArtifact.contains("Test error message"));
        }

        @Test
        void executeWorkflow_metricsRecordedForEachStep() throws Exception {
                lenient().when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                lenient().when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                lenient().when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                setupDeveloperStepMocks();
                lenient().when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                lenient().when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(metrics).recordAgentStepExecution(eq("PM"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("QUALIFIER"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("ARCHITECT"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("DEVELOPER"), eq("generate"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("REVIEW"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("TESTER"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("WRITER"), eq("execute"), anyLong());
        }

        @Test
        void executeWorkflow_interruptBlocksWorkflow_setsEscalatedStatus() throws Exception {
                lenient().when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                lenient().when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                lenient().when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");

                // Override interrupt mock to BLOCK on developer generate
                DynamicInterruptService.InterruptDecision blockDecision =
                                DynamicInterruptService.InterruptDecision.block("force_push_blocked", "Force push is not allowed");
                when(interruptService.evaluate(eq("DEVELOPER"), any(), eq("generate"), any(), any()))
                                .thenReturn(blockDecision);

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(developerStep, never()).generateCode(any(RunContext.class));
                verify(runRepository, atLeastOnce()).save(argThat(run -> run.getStatus() == RunStatus.ESCALATED));
                verify(metrics).recordWorkflowEscalation(anyLong());
        }

        @Test
        void executeWorkflow_majorityVotingVeto_setsEscalatedStatus() throws Exception {
                lenient().when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                lenient().when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                lenient().when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                setupDeveloperStepMocks();
                lenient().when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");

                // Override judge majority voting to return veto
                JudgeService.JudgeVerdict vetoVerdict = new JudgeService.JudgeVerdict(
                                UUID.randomUUID(), "pre_merge", "persona_review_report", "code_quality",
                                0.30, "veto", 0.8, Map.of(),
                                List.of(), "VETO: Artifact fails quality bar.",
                                null, Instant.now());
                when(judgeService.evaluateWithMajorityVoting(any(), anyString(), anyString()))
                                .thenReturn(vetoVerdict);

                workflowEngine.executeWorkflow(runEntity.getId());

                // Judge veto comes after tester; writer should be blocked
                verify(testerStep).execute(any(RunContext.class));
                verify(writerStep, never()).execute(any(RunContext.class));
                verify(runRepository, atLeastOnce()).save(argThat(run -> run.getStatus() == RunStatus.ESCALATED));
                verify(metrics).recordWorkflowEscalation(anyLong());
        }

        @Test
        void executeWorkflow_metricsRecordedOnStepError() throws Exception {
                lenient().when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                lenient().when(qualifierStep.execute(any(RunContext.class)))
                                .thenThrow(new RuntimeException("Qualifier failed"));

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(metrics).recordAgentStepExecution(eq("PM"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepError(eq("QUALIFIER"), eq("execute"));
                verify(metrics, never()).recordAgentStepExecution(eq("ARCHITECT"), eq("execute"), anyLong());
        }
}
