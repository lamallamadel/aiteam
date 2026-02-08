package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@TestPropertySource(properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb",
                "spring.datasource.driverClassName=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Import(WorkflowEngineIntegrationTest.TestConfiguration.class)
class WorkflowEngineIntegrationTest {

        @Autowired
        private RunRepository runRepository;

        @MockBean
        private JsonSchemaValidator schemaValidator;

        @MockBean
        private PmStep pmStep;

        @MockBean
        private QualifierStep qualifierStep;

        @MockBean
        private ArchitectStep architectStep;

        @MockBean
        private DeveloperStep developerStep;

        @MockBean
        private PersonaReviewService personaReviewService;

        @MockBean
        private TesterStep testerStep;

        @MockBean
        private WriterStep writerStep;

        @MockBean
        private OrchestratorMetrics metrics;

        private WorkflowEngine workflowEngine;

        @org.springframework.context.annotation.Configuration
        static class TestConfiguration {
                @org.springframework.context.annotation.Bean
                public WorkflowEngine workflowEngine(
                                RunRepository runRepository,
                                JsonSchemaValidator schemaValidator,
                                PmStep pmStep,
                                QualifierStep qualifierStep,
                                ArchitectStep architectStep,
                                DeveloperStep developerStep,
                                PersonaReviewService personaReviewService,
                                TesterStep testerStep,
                                WriterStep writerStep,
                                OrchestratorMetrics metrics) {
                        return new WorkflowEngine(
                                        runRepository,
                                        schemaValidator,
                                        pmStep,
                                        qualifierStep,
                                        architectStep,
                                        developerStep,
                                        personaReviewService,
                                        testerStep,
                                        writerStep,
                                        metrics);
                }
        }

        @BeforeEach
        void setUp() throws Exception {
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
                                metrics);

                when(pmStep.execute(any(RunContext.class))).thenReturn("{\"issueId\":123}");
                when(qualifierStep.execute(any(RunContext.class))).thenReturn("{\"tasks\":[]}");
                when(architectStep.execute(any(RunContext.class))).thenReturn("Architecture notes");
                when(developerStep.execute(any(RunContext.class))).thenReturn("https://github.com/owner/repo/pull/1");
                when(testerStep.execute(any(RunContext.class))).thenReturn("{\"ciStatus\":\"GREEN\"}");
                when(writerStep.execute(any(RunContext.class))).thenReturn("Docs updated");
        }

        @Test
        void executeWorkflow_persistsRunToDatabase() throws Exception {
                RunEntity runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                runEntity = runRepository.save(runEntity);
                assertNotNull(runEntity.getId());

                workflowEngine.executeWorkflow(runEntity.getId());

                RunEntity savedRun = runRepository.findById(runEntity.getId()).orElseThrow();
                assertEquals(RunStatus.DONE, savedRun.getStatus());
                assertNull(savedRun.getCurrentAgent());
        }

        @Test
        void executeWorkflow_persistsArtifacts() throws Exception {
                RunEntity runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                runEntity = runRepository.save(runEntity);

                workflowEngine.executeWorkflow(runEntity.getId());

                RunEntity savedRun = runRepository.findById(runEntity.getId()).orElseThrow();
                assertFalse(savedRun.getArtifacts().isEmpty());
                assertTrue(savedRun.getArtifacts().size() >= 6);

                assertTrue(savedRun.getArtifacts().stream()
                                .anyMatch(a -> "PM".equals(a.getAgentName())));
                assertTrue(savedRun.getArtifacts().stream()
                                .anyMatch(a -> "TESTER".equals(a.getAgentName())));
        }

        @Test
        void executeWorkflow_updatesStatusThroughSteps() throws Exception {
                RunEntity runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                runEntity = runRepository.save(runEntity);

                when(pmStep.execute(any(RunContext.class))).thenAnswer(invocation -> {
                        RunContext ctx = invocation.getArgument(0);
                        RunEntity run = runRepository.findById(ctx.getRunEntity().getId()).orElseThrow();
                        assertEquals(RunStatus.PM, run.getStatus());
                        assertEquals("PM", run.getCurrentAgent());
                        return "{\"issueId\":123}";
                });

                workflowEngine.executeWorkflow(runEntity.getId());

                RunEntity finalRun = runRepository.findById(runEntity.getId()).orElseThrow();
                assertEquals(RunStatus.DONE, finalRun.getStatus());
        }

        @Test
        void executeWorkflow_failureRollbacksTransaction() throws Exception {
                RunEntity runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                runEntity = runRepository.save(runEntity);

                when(qualifierStep.execute(any(RunContext.class)))
                                .thenThrow(new RuntimeException("Step failed"));

                workflowEngine.executeWorkflow(runEntity.getId());

                RunEntity savedRun = runRepository.findById(runEntity.getId()).orElseThrow();
                assertEquals(RunStatus.FAILED, savedRun.getStatus());
        }

        @Test
        void executeWorkflow_escalationPersistsToDatabase() throws Exception {
                RunEntity runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                runEntity = runRepository.save(runEntity);

                String escalationJson = "{\"context\":\"test\",\"blocker\":\"test\",\"evidence\":[],\"options\":[],\"recommendation\":\"test\",\"decisionNeeded\":\"test\"}";
                when(testerStep.execute(any(RunContext.class)))
                                .thenThrow(new EscalationException(escalationJson));

                workflowEngine.executeWorkflow(runEntity.getId());

                RunEntity savedRun = runRepository.findById(runEntity.getId()).orElseThrow();
                assertEquals(RunStatus.ESCALATED, savedRun.getStatus());

                assertTrue(savedRun.getArtifacts().stream()
                                .anyMatch(a -> "escalation.json".equals(a.getArtifactType())));
        }

        @Test
        void executeWorkflow_multipleRuns_independentlyPersisted() throws Exception {
                RunEntity run1 = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                RunEntity run2 = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                456,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                run1 = runRepository.save(run1);
                run2 = runRepository.save(run2);

                workflowEngine.executeWorkflow(run1.getId());
                workflowEngine.executeWorkflow(run2.getId());

                RunEntity savedRun1 = runRepository.findById(run1.getId()).orElseThrow();
                RunEntity savedRun2 = runRepository.findById(run2.getId()).orElseThrow();

                assertEquals(RunStatus.DONE, savedRun1.getStatus());
                assertEquals(RunStatus.DONE, savedRun2.getStatus());
                assertEquals(123, savedRun1.getIssueNumber());
                assertEquals(456, savedRun2.getIssueNumber());
        }

        @Test
        void executeWorkflow_queriesRunById() {
                RunEntity runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                789,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                runEntity = runRepository.save(runEntity);

                RunEntity found = runRepository.findById(runEntity.getId()).orElseThrow();
                assertEquals(789, found.getIssueNumber());
                assertEquals("owner/repo", found.getRepo());
        }

        @Test
        void executeWorkflow_artifactsHaveTimestamps() throws Exception {
                RunEntity runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                runEntity = runRepository.save(runEntity);

                workflowEngine.executeWorkflow(runEntity.getId());

                RunEntity savedRun = runRepository.findById(runEntity.getId()).orElseThrow();

                savedRun.getArtifacts().forEach(artifact -> {
                        assertNotNull(artifact.getCreatedAt());
                        assertNotNull(artifact.getAgentName());
                        assertNotNull(artifact.getArtifactType());
                });
        }

        @Test
        void executeWorkflow_ciFixCountIncremented() throws Exception {
                RunEntity runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                runEntity = runRepository.save(runEntity);
                UUID runId = runEntity.getId();

                when(testerStep.execute(any(RunContext.class))).thenAnswer(invocation -> {
                        RunContext ctx = invocation.getArgument(0);
                        ctx.getRunEntity().incrementCiFixCount();
                        ctx.getRunEntity().incrementE2eFixCount();
                        return "{\"ciStatus\":\"GREEN\"}";
                });

                workflowEngine.executeWorkflow(runEntity.getId());

                RunEntity savedRun = runRepository.findById(runId).orElseThrow();
                assertEquals(1, savedRun.getCiFixCount());
                assertEquals(1, savedRun.getE2eFixCount());
        }

        @Test
        void executeWorkflow_errorArtifactIncludesDetails() throws Exception {
                RunEntity runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.RECEIVED,
                                Instant.now());

                runEntity = runRepository.save(runEntity);

                when(architectStep.execute(any(RunContext.class)))
                                .thenThrow(new RuntimeException("Architecture analysis failed"));

                workflowEngine.executeWorkflow(runEntity.getId());

                RunEntity savedRun = runRepository.findById(runEntity.getId()).orElseThrow();

                assertTrue(savedRun.getArtifacts().stream()
                                .anyMatch(a -> "error_details".equals(a.getArtifactType())));

                String errorPayload = savedRun.getArtifacts().stream()
                                .filter(a -> "error_details".equals(a.getArtifactType()))
                                .findFirst()
                                .map(com.atlasia.ai.model.RunArtifactEntity::getPayload)
                                .orElse("");

                assertTrue(errorPayload.contains("Architecture analysis failed"));
                assertTrue(errorPayload.contains("ARCHITECT"));
        }
}
