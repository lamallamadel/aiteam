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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

    @Autowired
    @Lazy
    private WorkflowEngine self;

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
    public void executeWorkflowAsync(UUID runId, String gitHubToken) {
        String correlationId = CorrelationIdHolder.generateCorrelationId();
        CorrelationIdHolder.setCorrelationId(correlationId);
        CorrelationIdHolder.setRunId(runId);
        CorrelationIdHolder.setGitHubToken(gitHubToken);

        try {
            log.info("Starting async workflow execution: runId={}, correlationId={}", runId, correlationId);
            self.executeWorkflow(runId);
        } finally {
            CorrelationIdHolder.clear();
        }
    }

    @Transactional
    public void executeWorkflow(UUID runId) {
        RunEntity runEntity = runRepository.findById(runId).orElseThrow(
                () -> new WorkflowException("Run not found: " + runId, runId, "INIT",
                        OrchestratorException.RecoveryStrategy.FAIL_FAST));

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
            workflowTimer.stop(metrics.getWorkflowDuration());
            metrics.recordWorkflowSuccess(duration);

            log.info("Workflow completed successfully: runId={}, duration={}ms, correlationId={}",
                    runEntity.getId(), duration, CorrelationIdHolder.getCorrelationId());

        } catch (EscalationException e) {
            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.getWorkflowDuration());
            metrics.recordWorkflowEscalation(duration);
            handleEscalation(runEntity, e);
        } catch (OrchestratorException e) {
            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.getWorkflowDuration());
            metrics.recordWorkflowFailure(duration);
            handleOrchestratorException(runEntity, e);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.getWorkflowDuration());
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
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(agentName, "execute", duration);

            log.info("PM step completed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
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
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(agentName, "execute", duration);

            log.info("Qualifier step completed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
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
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(agentName, "execute", duration);

            log.info("Architect step completed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
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
            log.info("Executing Developer step (code generation only): runId={}, correlationId={}",
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());

            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.DEVELOPER);
            runEntity.setCurrentAgent(agentName);
            runRepository.save(runEntity);

            developerStep.generateCode(context);

            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(agentName, "generate", duration);

            log.info("Developer step (code generation) completed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepError(agentName, "generate");

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

            PersonaReviewService.PersonaReviewReport report = personaReviewService.reviewCodeChanges(context,
                    codeChanges);

            String reportJson = serializeReviewReport(report);

            schemaValidator.validate(reportJson, "persona_review.schema.json");

            persistArtifact(runEntity, agentName, "persona_review_report", reportJson);

            String prUrl = developerStep.commitAndCreatePullRequest(context, codeChanges);
            persistArtifact(runEntity, "DEVELOPER", "pr_url", prUrl);

            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(agentName, "execute", duration);

            log.info(
                    "Persona Review step completed: runId={}, duration={}ms, findings={}, securityFixesApplied={}, prUrl={}, correlationId={}",
                    context.getRunEntity().getId(), duration, report.getFindings().size(),
                    report.isSecurityFixesApplied(), prUrl, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
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
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(agentName, "execute", duration);

            log.info("Tester step completed: runId={}, duration={}ms, ciFixCount={}, e2eFixCount={}, correlationId={}",
                    context.getRunEntity().getId(), duration, runEntity.getCiFixCount(),
                    runEntity.getE2eFixCount(), CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
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
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(agentName, "execute", duration);

            log.info("Writer step completed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
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
                Instant.now());
        runEntity.addArtifact(artifact);
        runRepository.save(runEntity);

        log.debug("Persisted artifact: runId={}, agentName={}, artifactType={}, correlationId={}",
                runEntity.getId(), agentName, artifactType, CorrelationIdHolder.getCorrelationId());
    }

    private String serializeReviewReport(PersonaReviewService.PersonaReviewReport report) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> reportData = new java.util.HashMap<>();

            reportData.put("personaName", "merged");

            java.util.List<java.util.Map<String, Object>> riskFindings = new java.util.ArrayList<>();
            for (PersonaReviewService.PersonaReview review : report.getFindings()) {
                for (PersonaReviewService.PersonaIssue issue : review.getIssues()) {
                    java.util.Map<String, Object> finding = new java.util.HashMap<>();
                    finding.put("severity", issue.getSeverity());
                    finding.put("category", review.getPersonaRole());
                    finding.put("description", issue.getDescription());
                    finding.put("location", issue.getFilePath());
                    finding.put("impact", issue.getRecommendation());
                    riskFindings.add(finding);
                }
            }
            reportData.put("riskFindings", riskFindings);

            java.util.List<java.util.Map<String, Object>> enhancements = new java.util.ArrayList<>();
            for (PersonaReviewService.PersonaReview review : report.getFindings()) {
                for (PersonaReviewService.PersonaEnhancement enhancement : review.getEnhancements()) {
                    java.util.Map<String, Object> enh = new java.util.HashMap<>();
                    enh.put("type", enhancement.getCategory());
                    enh.put("description", enhancement.getDescription());
                    enh.put("priority", "medium");
                    enh.put("suggestedApproach", enhancement.getBenefit());
                    enhancements.add(enh);
                }
            }
            reportData.put("requiredEnhancements", enhancements);

            java.util.Map<String, Object> assessment = new java.util.HashMap<>();

            int criticalCount = 0, highCount = 0, mediumCount = 0, lowCount = 0;
            for (PersonaReviewService.PersonaReview review : report.getFindings()) {
                for (PersonaReviewService.PersonaIssue issue : review.getIssues()) {
                    switch (issue.getSeverity().toLowerCase()) {
                        case "critical" -> criticalCount++;
                        case "high" -> highCount++;
                        case "medium" -> mediumCount++;
                        case "low" -> lowCount++;
                    }
                }
            }

            String status;
            if (criticalCount > 0) {
                status = "rejected";
            } else if (report.isSecurityFixesApplied() || highCount > 0) {
                status = "changes_required";
            } else if (mediumCount > 0 || lowCount > 0) {
                status = "approved_with_minor_issues";
            } else {
                status = "approved";
            }

            assessment.put("status", status);
            assessment.put("summary", "Reviewed by " + report.getFindings().size() + " personas. " +
                    "Found " + criticalCount + " critical, " + highCount + " high, " +
                    mediumCount + " medium, and " + lowCount + " low severity issues." +
                    (report.isSecurityFixesApplied() ? " Security fixes have been auto-applied." : ""));
            assessment.put("criticalIssueCount", criticalCount);
            assessment.put("highIssueCount", highCount);
            assessment.put("mediumIssueCount", mediumCount);
            assessment.put("lowIssueCount", lowCount);
            reportData.put("overallAssessment", assessment);

            java.util.List<String> positiveFindings = new java.util.ArrayList<>();
            java.util.List<String> recommendations = new java.util.ArrayList<>();
            for (PersonaReviewService.PersonaReview review : report.getFindings()) {
                if (review.getOverallAssessment() != null
                        && !review.getOverallAssessment().toLowerCase().contains("failed")) {
                    positiveFindings.add("[" + review.getPersonaName() + "] " + review.getOverallAssessment());
                }
            }
            recommendations.addAll(report.getMergedRecommendations());

            reportData.put("positiveFindings", positiveFindings);
            reportData.put("recommendations", recommendations);

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(reportData);
        } catch (Exception e) {
            log.error("Failed to serialize review report, correlationId={}",
                    CorrelationIdHolder.getCorrelationId(), e);
            return "{\"error\": \"Failed to serialize report\"}";
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
        log.error(
                "Workflow failed with orchestrator exception: runId={}, errorCode={}, recoveryStrategy={}, retryable={}, correlationId={}",
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
                    CorrelationIdHolder.getCorrelationId());

            persistArtifact(runEntity, runEntity.getCurrentAgent() != null ? runEntity.getCurrentAgent() : "UNKNOWN",
                    "error_details", errorDetails);
        } catch (Exception persistError) {
            log.error("Failed to persist error artifact: runId={}, correlationId={}",
                    runEntity.getId(), CorrelationIdHolder.getCorrelationId(), persistError);
        }
    }
}
