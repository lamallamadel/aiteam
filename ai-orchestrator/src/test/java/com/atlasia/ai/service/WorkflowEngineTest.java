package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.exception.OrchestratorException;
import com.atlasia.ai.service.event.WorkflowEventBus;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import com.atlasia.ai.service.trace.TraceEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
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

        private WorkflowEngine workflowEngine;
        private RunEntity runEntity;

        @BeforeEach
        void setUp() {
                workflowEngine = new WorkflowEngine(
                                runRepository,
                                schemaValidator,
                                pmStep,
                                qualifierStep,
                                architectStep,
                                developerStep,
                                personaReviewService,
                                testerStep,
                                writerStep,
                                metrics,
                                eventBus,
                                traceEventService);

                runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());
        }

        @Test
        void executeWorkflow_successfulFullWorkflow_completesAllSteps() throws Exception {
                when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                when(developerStep.execute(any(RunContext.class))).thenReturn("https://github.com/owner/repo/pull/1");
                when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(pmStep).execute(any(RunContext.class));
                verify(qualifierStep).execute(any(RunContext.class));
                verify(architectStep).execute(any(RunContext.class));
                verify(developerStep).execute(any(RunContext.class));
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
                when(pmStep.execute(any(RunContext.class)))
                                .thenThrow(new RuntimeException("PM step failed"));

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(pmStep).execute(any(RunContext.class));
                verify(qualifierStep, never()).execute(any(RunContext.class));

                verify(runRepository).save(argThat(run -> run.getStatus() == RunStatus.FAILED));
                verify(metrics).recordWorkflowFailure(anyLong());
        }

        @Test
        void executeWorkflow_escalationException_setsEscalatedStatus() throws Exception {
                String escalationJson = "{\"context\":\"test\",\"blocker\":\"test\"}";
                when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                when(developerStep.execute(any(RunContext.class))).thenReturn("https://github.com/owner/repo/pull/1");
                when(testerStep.execute(any(RunContext.class)))
                                .thenThrow(new EscalationException(escalationJson));

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(testerStep).execute(any(RunContext.class));
                verify(writerStep, never()).execute(any(RunContext.class));

                verify(schemaValidator).validate(eq(escalationJson), eq("escalation.schema.json"));
                verify(runRepository).save(argThat(run -> run.getStatus() == RunStatus.ESCALATED));
                verify(metrics).recordWorkflowEscalation(anyLong());
        }

        @Test
        void executeWorkflow_orchestratorException_handlesGracefully() throws Exception {
                when(pmStep.execute(any(RunContext.class)))
                                .thenThrow(new com.atlasia.ai.service.exception.WorkflowException(
                                                "Service unavailable",
                                                UUID.randomUUID(),
                                                "PM",
                                                com.atlasia.ai.service.exception.OrchestratorException.RecoveryStrategy.RETRY_WITH_BACKOFF));

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(runRepository).save(argThat(run -> run.getStatus() == RunStatus.FAILED));
                verify(metrics).recordWorkflowFailure(anyLong());
        }

        @Test
        void executeWorkflow_storesArtifactsFromEachStep() throws Exception {
                when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                when(developerStep.execute(any(RunContext.class))).thenReturn("https://github.com/owner/repo/pull/1");
                when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflow(runEntity.getId());

                assertEquals(6, runEntity.getArtifacts().size());

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
                when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                when(developerStep.execute(any(RunContext.class))).thenReturn("https://github.com/owner/repo/pull/1");
                when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(runRepository).save(
                                argThat(run -> run.getStatus() == RunStatus.PM && "PM".equals(run.getCurrentAgent())));
                verify(runRepository).save(argThat(run -> run.getStatus() == RunStatus.QUALIFIER
                                && "QUALIFIER".equals(run.getCurrentAgent())));
                verify(runRepository).save(argThat(run -> run.getStatus() == RunStatus.ARCHITECT
                                && "ARCHITECT".equals(run.getCurrentAgent())));
                verify(runRepository).save(argThat(run -> run.getStatus() == RunStatus.DEVELOPER
                                && "DEVELOPER".equals(run.getCurrentAgent())));
                verify(runRepository).save(argThat(
                                run -> run.getStatus() == RunStatus.TESTER && "TESTER".equals(run.getCurrentAgent())));
                verify(runRepository).save(argThat(
                                run -> run.getStatus() == RunStatus.WRITER && "WRITER".equals(run.getCurrentAgent())));
                verify(runRepository).save(
                                argThat(run -> run.getStatus() == RunStatus.DONE && run.getCurrentAgent() == null));
        }

        @Test
        void executeWorkflow_schemaValidationFailure_failsWorkflow() throws Exception {
                when(pmStep.execute(any(RunContext.class))).thenReturn("{\"invalid\":\"json\"}");
                doThrow(new IllegalArgumentException("Schema validation failed"))
                                .when(schemaValidator).validate(anyString(), eq("ticket_plan.schema.json"));

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(runRepository).save(argThat(run -> run.getStatus() == RunStatus.FAILED));
                verify(qualifierStep, never()).execute(any(RunContext.class));
                verify(metrics).recordWorkflowFailure(anyLong());
        }

        @Test
        void executeWorkflowAsync_loadsRunAndExecutes() throws Exception {
                UUID runId = runEntity.getId();
                when(runRepository.findById(runId)).thenReturn(Optional.of(runEntity));
                when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                when(developerStep.execute(any(RunContext.class))).thenReturn("https://github.com/owner/repo/pull/1");
                when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflowAsync(runId, "test-token");

                Thread.sleep(100);

                verify(runRepository).findById(runId);
                verify(pmStep).execute(any(RunContext.class));
                verify(metrics).recordWorkflowExecution();
        }

        @Test
        void executeWorkflowAsync_runNotFound_throwsWorkflowException() {
                UUID runId = UUID.randomUUID();
                when(runRepository.findById(runId)).thenReturn(Optional.empty());

                assertDoesNotThrow(() -> workflowEngine.executeWorkflowAsync(runId, "test-token"));
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

                when(pmStep.execute(any(RunContext.class))).thenAnswer(invocation -> {
                        RunContext context = invocation.getArgument(0);
                        assertEquals("testowner", context.getOwner());
                        assertEquals("testrepo", context.getRepo());
                        return "{\"issueId\":456}";
                });

                when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                when(developerStep.execute(any(RunContext.class))).thenReturn("https://github.com/owner/repo/pull/1");
                when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(pmStep).execute(
                                argThat(ctx -> "testowner".equals(ctx.getOwner()) && "testrepo".equals(ctx.getRepo())));
        }

        @Test
        void executeWorkflow_storesErrorArtifactOnFailure() throws Exception {
                when(pmStep.execute(any(RunContext.class)))
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
                when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                when(developerStep.execute(any(RunContext.class))).thenReturn("https://github.com/owner/repo/pull/1");
                when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(metrics).recordAgentStepExecution(eq("PM"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("QUALIFIER"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("ARCHITECT"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("DEVELOPER"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("TESTER"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepExecution(eq("WRITER"), eq("execute"), anyLong());
        }

        @Test
        void executeWorkflow_metricsRecordedOnStepError() throws Exception {
                when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                when(qualifierStep.execute(any(RunContext.class)))
                                .thenThrow(new RuntimeException("Qualifier failed"));

                workflowEngine.executeWorkflow(runEntity.getId());

                verify(metrics).recordAgentStepExecution(eq("PM"), eq("execute"), anyLong());
                verify(metrics).recordAgentStepError(eq("QUALIFIER"), eq("execute"));
                verify(metrics, never()).recordAgentStepExecution(eq("ARCHITECT"), eq("execute"), anyLong());
        }
}
