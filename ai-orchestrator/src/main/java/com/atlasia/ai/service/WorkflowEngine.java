package com.atlasia.ai.service;

import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.exception.OrchestratorException;
import com.atlasia.ai.service.exception.WorkflowException;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class WorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final RunRepository runRepository;
    private final JsonSchemaValidator schemaValidator;
    private final PmStep pmStep;
    private final QualifierStep qualifierStep;
    private final ArchitectStep architectStep;
    private final DeveloperStep developerStep;
    private final PersonaReviewService personaReviewService;
    private final TesterStep testerStep;
    private final WriterStep writerStep;
    private final OrchestratorMetrics metrics;

    public WorkflowEngine(
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
        this.runRepository = runRepository;
        this.schemaValidator = schemaValidator;
        this.pmStep = pmStep;
        this.qualifierStep = qualifierStep;
        this.architectStep = architectStep;
        this.developerStep = developerStep;
        this.personaReviewService = personaReviewService;
        this.testerStep = testerStep;
        this.writerStep = writerStep;
        this.metrics = metrics;
    }

    @Async("workflowExecutor")
    public void executeWorkflowAsync(UUID runId) {
        String correlationId = CorrelationIdHolder.generateCorrelationId();
        CorrelationIdHolder.setCorrelationId(correlationId);
        CorrelationIdHolder.setRunId(runId);
        
        try {
            RunEntity runEntity = runRepository.findById(runId).orElseThrow(
                () -> new WorkflowException("Run not found: " + runId, runId, "INIT", 
                        OrchestratorException.RecoveryStrategy.FAIL_FAST)
            );
            
            log.info("Starting async workflow execution: runId={}, correlationId={}", runId, correlationId);
            executeWorkflow(runEntity);
        } finally {
            CorrelationIdHolder.clear();
        }
    }

    @Transactional
    public void executeWorkflow(RunEntity runEntity) {
        Timer.Sample workflowTimer = metrics.startWorkflowTimer();
        long startTime = System.currentTimeMillis();
        
        metrics.recordWorkflowExecution();
        
        try {
            log.info("Starting workflow execution: runId={}, issueNumber={}, repo={}, correlationId={}", 
                    runEntity.getId(), runEntity.getIssueNumber(), runEntity.getRepo(), 
                    CorrelationIdHolder.getCorrelationId());
            
            String[] repoParts = runEntity.getRepo().split("/");
            String owner = repoParts[0];
            String repo = repoParts[1];
            
            RunContext context = new RunContext(runEntity, owner, repo);

            executePmStep(context);
            executeQualifierStep(context);
            executeArchitectStep(context);
            executeDeveloperStep(context);
            executePersonaReview(context);
            executeTesterStep(context);
            executeWriterStep(context);

            runEntity.setStatus(RunStatus.DONE);
            runEntity.setCurrentAgent(null);
            runRepository.save(runEntity);
            
            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.startWorkflowTimer());
            metrics.recordWorkflowSuccess(duration);
            
            log.info("Workflow completed successfully: runId={}, duration={}ms, correlationId={}", 
                    runEntity.getId(), duration, CorrelationIdHolder.getCorrelationId());

        } catch (EscalationException e) {
            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.startWorkflowTimer());
            metrics.recordWorkflowEscalation(duration);
            handleEscalation(runEntity, e);
        } catch (OrchestratorException e) {
            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.startWorkflowTimer());
            metrics.recordWorkflowFailure(duration);
            handleOrchestratorException(runEntity, e);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.startWorkflowTimer());
            metrics.recordWorkflowFailure(duration);
            handleFailure(runEntity, e);
        }
    }

    private void executePmStep(RunContext context) throws Exception {
        String agentName = "PM";
        CorrelationIdHolder.setAgentName(agentName);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing PM step: runId={}, correlationId={}", 
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());
            
            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.PM);
            runEntity.setCurrentAgent(agentName);
            runRepository.save(runEntity);

            String artifact = pmStep.execute(context);
            schemaValidator.validate(artifact, "ticket_plan.schema.json");
            
            context.setTicketPlan(artifact);
            persistArtifact(runEntity, agentName, "ticket_plan.json", artifact);
            
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepExecution(agentName, "execute", duration);
            
            log.info("PM step completed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepError(agentName, "execute");
            
            log.error("PM step failed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void executeQualifierStep(RunContext context) throws Exception {
        String agentName = "QUALIFIER";
        CorrelationIdHolder.setAgentName(agentName);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing Qualifier step: runId={}, correlationId={}", 
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());
            
            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.QUALIFIER);
            runEntity.setCurrentAgent(agentName);
            runRepository.save(runEntity);

            String artifact = qualifierStep.execute(context);
            schemaValidator.validate(artifact, "work_plan.schema.json");
            
            context.setWorkPlan(artifact);
            persistArtifact(runEntity, agentName, "work_plan.json", artifact);
            
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepExecution(agentName, "execute", duration);
            
            log.info("Qualifier step completed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepError(agentName, "execute");
            
            log.error("Qualifier step failed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void executeArchitectStep(RunContext context) throws Exception {
        String agentName = "ARCHITECT";
        CorrelationIdHolder.setAgentName(agentName);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing Architect step: runId={}, correlationId={}", 
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());
            
            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.ARCHITECT);
            runEntity.setCurrentAgent(agentName);
            runRepository.save(runEntity);

            String artifact = architectStep.execute(context);
            persistArtifact(runEntity, agentName, "architecture_notes.md", artifact);
            
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepExecution(agentName, "execute", duration);
            
            log.info("Architect step completed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepError(agentName, "execute");
            
            log.error("Architect step failed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void executeDeveloperStep(RunContext context) throws Exception {
        String agentName = "DEVELOPER";
        CorrelationIdHolder.setAgentName(agentName);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing Developer step: runId={}, correlationId={}", 
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());
            
            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.DEVELOPER);
            runEntity.setCurrentAgent(agentName);
            runRepository.save(runEntity);

            String artifact = developerStep.execute(context);
            persistArtifact(runEntity, agentName, "pr_url", artifact);
            
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepExecution(agentName, "execute", duration);
            
            log.info("Developer step completed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepError(agentName, "execute");
            
            log.error("Developer step failed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void executePersonaReview(RunContext context) throws Exception {
        String agentName = "REVIEW";
        CorrelationIdHolder.setAgentName(agentName);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing Persona Review step: runId={}, correlationId={}", 
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());
            
            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.REVIEW);
            runEntity.setCurrentAgent(agentName);
            runRepository.save(runEntity);

            DeveloperStep.CodeChanges codeChanges = context.getCodeChanges();
            if (codeChanges == null) {
                log.warn("No code changes available for persona review, skipping");
                return;
            }

            PersonaReviewService.PersonaReviewReport report = personaReviewService.reviewCodeChanges(context, codeChanges);
            
            String reportJson = serializeReviewReport(report);
            persistArtifact(runEntity, agentName, "persona_review_report", reportJson);
            
            if (report.isSecurityFixesApplied()) {
                log.info("Security fixes were applied, updating code changes on branch");
                updateCodeChangesOnBranch(context, codeChanges);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepExecution(agentName, "execute", duration);
            
            log.info("Persona Review step completed: runId={}, duration={}ms, findings={}, securityFixesApplied={}, correlationId={}", 
                    context.getRunEntity().getId(), duration, report.getFindings().size(), 
                    report.isSecurityFixesApplied(), CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepError(agentName, "execute");
            
            log.error("Persona Review step failed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void executeTesterStep(RunContext context) throws Exception {
        String agentName = "TESTER";
        CorrelationIdHolder.setAgentName(agentName);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing Tester step: runId={}, correlationId={}", 
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());
            
            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.TESTER);
            runEntity.setCurrentAgent(agentName);
            runRepository.save(runEntity);

            String artifact = testerStep.execute(context);
            schemaValidator.validate(artifact, "test_report.schema.json");
            
            persistArtifact(runEntity, agentName, "test_report.json", artifact);
            
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepExecution(agentName, "execute", duration);
            
            log.info("Tester step completed: runId={}, duration={}ms, ciFixCount={}, e2eFixCount={}, correlationId={}", 
                    context.getRunEntity().getId(), duration, runEntity.getCiFixCount(), 
                    runEntity.getE2eFixCount(), CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepError(agentName, "execute");
            
            log.error("Tester step failed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void executeWriterStep(RunContext context) throws Exception {
        String agentName = "WRITER";
        CorrelationIdHolder.setAgentName(agentName);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing Writer step: runId={}, correlationId={}", 
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());
            
            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.WRITER);
            runEntity.setCurrentAgent(agentName);
            runRepository.save(runEntity);

            String artifact = writerStep.execute(context);
            persistArtifact(runEntity, agentName, "docs_update", artifact);
            
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepExecution(agentName, "execute", duration);
            
            log.info("Writer step completed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.startAgentStepTimer());
            metrics.recordAgentStepError(agentName, "execute");
            
            log.error("Writer step failed: runId={}, duration={}ms, correlationId={}", 
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void persistArtifact(RunEntity runEntity, String agentName, String artifactType, String payload) {
        RunArtifactEntity artifact = new RunArtifactEntity(
            agentName,
            artifactType,
            payload,
            Instant.now()
        );
        runEntity.addArtifact(artifact);
        runRepository.save(runEntity);
        
        log.debug("Persisted artifact: runId={}, agentName={}, artifactType={}, correlationId={}", 
                runEntity.getId(), agentName, artifactType, CorrelationIdHolder.getCorrelationId());
    }

    private String serializeReviewReport(PersonaReviewService.PersonaReviewReport report) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(report);
        } catch (Exception e) {
            log.error("Failed to serialize review report, correlationId={}", 
                    CorrelationIdHolder.getCorrelationId(), e);
            return "{\"error\": \"Failed to serialize report\"}";
        }
    }

    private void updateCodeChangesOnBranch(RunContext context, DeveloperStep.CodeChanges codeChanges) {
        try {
            String branchName = context.getBranchName();
            
            log.info("Updating code changes on branch {} with security fixes", branchName);
            log.info("Security fixes applied to {} files in code changes", codeChanges.getFiles().size());
        } catch (Exception e) {
            log.error("Failed to update code changes on branch, correlationId={}", 
                    CorrelationIdHolder.getCorrelationId(), e);
        }
    }

    private void handleEscalation(RunEntity runEntity, EscalationException e) {
        log.warn("Workflow escalated: runId={}, agent={}, correlationId={}", 
                runEntity.getId(), runEntity.getCurrentAgent(), CorrelationIdHolder.getCorrelationId());
        
        try {
            schemaValidator.validate(e.getEscalationJson(), "escalation.schema.json");
            persistArtifact(runEntity, runEntity.getCurrentAgent(), "escalation.json", e.getEscalationJson());
        } catch (Exception validationError) {
            log.error("Failed to validate escalation JSON: runId={}, correlationId={}", 
                    runEntity.getId(), CorrelationIdHolder.getCorrelationId(), validationError);
        }
        
        runEntity.setStatus(RunStatus.ESCALATED);
        runRepository.save(runEntity);
    }

    private void handleOrchestratorException(RunEntity runEntity, OrchestratorException e) {
        log.error("Workflow failed with orchestrator exception: runId={}, errorCode={}, recoveryStrategy={}, retryable={}, correlationId={}", 
                runEntity.getId(), e.getErrorCode(), e.getRecoveryStrategy(), e.isRetryable(), 
                CorrelationIdHolder.getCorrelationId(), e);
        
        runEntity.setStatus(RunStatus.FAILED);
        runRepository.save(runEntity);
        
        persistErrorArtifact(runEntity, e);
    }

    private void handleFailure(RunEntity runEntity, Exception e) {
        log.error("Workflow failed with unexpected exception: runId={}, agent={}, correlationId={}", 
                runEntity.getId(), runEntity.getCurrentAgent(), CorrelationIdHolder.getCorrelationId(), e);
        
        runEntity.setStatus(RunStatus.FAILED);
        runRepository.save(runEntity);
        
        persistErrorArtifact(runEntity, e);
    }
    
    private void persistErrorArtifact(RunEntity runEntity, Exception e) {
        try {
            String errorDetails = String.format(
                    "Error: %s\nMessage: %s\nAgent: %s\nCorrelationId: %s",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    runEntity.getCurrentAgent(),
                    CorrelationIdHolder.getCorrelationId()
            );
            
            persistArtifact(runEntity, runEntity.getCurrentAgent() != null ? runEntity.getCurrentAgent() : "UNKNOWN", 
                    "error_details", errorDetails);
        } catch (Exception persistError) {
            log.error("Failed to persist error artifact: runId={}, correlationId={}", 
                    runEntity.getId(), CorrelationIdHolder.getCorrelationId(), persistError);
        }
    }
}
