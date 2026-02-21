package com.atlasia.ai.service;

import com.atlasia.ai.model.EnvironmentLifecycle;
import com.atlasia.ai.model.EnvironmentSnapshot;
import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.AgentBindingService.AgentBinding;
import com.atlasia.ai.service.event.WorkflowEvent;
import com.atlasia.ai.service.event.WorkflowEventBus;
import com.atlasia.ai.service.exception.OrchestratorException;
import com.atlasia.ai.service.exception.WorkflowException;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import com.atlasia.ai.service.trace.TraceEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private static final int TOTAL_STEPS = 7;
    private static final int MAX_REVIEW_DEVELOPER_LOOPS = 2;
    private static final int MAX_TESTER_DEVELOPER_LOOPS = 2;

    private final RunRepository runRepository;
    private final JsonSchemaValidator schemaValidator;
    private final DeveloperStep developerStep;
    private final PersonaReviewService personaReviewService;
    private final OrchestratorMetrics metrics;
    private final WorkflowEventBus eventBus;
    private final TraceEventService traceEventService;
    private final BlackboardService blackboardService;
    private final DynamicInterruptService interruptService;
    private final JudgeService judgeService;
    private final AgentStepFactory agentStepFactory;
    private final AgentBindingService agentBindingService;
    private final ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private WorkflowEngine self;

    public WorkflowEngine(
            RunRepository runRepository,
            JsonSchemaValidator schemaValidator,
            DeveloperStep developerStep,
            PersonaReviewService personaReviewService,
            OrchestratorMetrics metrics,
            WorkflowEventBus eventBus,
            TraceEventService traceEventService,
            BlackboardService blackboardService,
            DynamicInterruptService interruptService,
            JudgeService judgeService,
            AgentStepFactory agentStepFactory,
            AgentBindingService agentBindingService,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.schemaValidator = schemaValidator;
        this.developerStep = developerStep;
        this.personaReviewService = personaReviewService;
        this.metrics = metrics;
        this.eventBus = eventBus;
        this.traceEventService = traceEventService;
        this.blackboardService = blackboardService;
        this.interruptService = interruptService;
        this.judgeService = judgeService;
        this.agentStepFactory = agentStepFactory;
        this.agentBindingService = agentBindingService;
        this.objectMapper = objectMapper;
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

            // --- Sequential Pipeline: PM → Qualifier → Architect → Developer ---
            if (!runEntity.isStepPruned("PM")) {
                emitStatus(runId, "IN_PROGRESS", "PM", progressPercent(0));
                executePmStep(context);
            } else {
                log.info("PM step pruned by user: runId={}", runId);
            }
            executeGraftsAfter(runId, "PM", context, runEntity);

            if (!runEntity.isStepPruned("QUALIFIER")) {
                emitStatus(runId, "IN_PROGRESS", "QUALIFIER", progressPercent(1));
                executeQualifierStep(context);
            } else {
                log.info("QUALIFIER step pruned by user: runId={}", runId);
            }
            executeGraftsAfter(runId, "QUALIFIER", context, runEntity);

            if (!runEntity.isStepPruned("ARCHITECT")) {
                emitStatus(runId, "IN_PROGRESS", "ARCHITECT", progressPercent(2));
                executeArchitectStep(context);
            } else {
                log.info("ARCHITECT step pruned by user: runId={}", runId);
            }
            executeGraftsAfter(runId, "ARCHITECT", context, runEntity);

            // --- Autonomy Gate: pause before code generation if confirm/observe ---
            if (!runEntity.isAutonomyDevGatePassed() &&
                    ("confirm".equals(runEntity.getAutonomy()) ||
                     "observe".equals(runEntity.getAutonomy()))) {
                log.info("Autonomy gate triggered (autonomy={}): runId={}", runEntity.getAutonomy(), runId);
                snapshotEnvironment(context);
                throw new EscalationException(buildLoopEscalation(
                        "pre_developer_autonomy_gate",
                        "Architecture plan is complete. Human approval required before code generation begins.",
                        runEntity.getAutonomy()));
            }

            // --- Graph-Based Loop: Developer ↔ Review ↔ Tester ---
            // Loop-back routing: review or tester can route back to developer
            // bounded by max_iterations to prevent infinite cycles.
            int reviewDeveloperLoops = 0;
            int testerDeveloperLoops = 0;

            emitStatus(runId, "IN_PROGRESS", "DEVELOPER", progressPercent(3));
            executeDeveloperStep(context);
            executeGraftsAfter(runId, "DEVELOPER", context, runEntity);

            // Review loop: developer → review → (tester | developer)
            boolean reviewApproved = runEntity.isStepPruned("REVIEW");
            if (reviewApproved) {
                log.info("REVIEW step pruned by user: runId={}", runId);
            }
            while (!reviewApproved) {
                emitStatus(runId, "IN_PROGRESS", "REVIEW", progressPercent(4));
                String reviewVerdict = executePersonaReviewWithVerdict(context);

                if ("approved".equals(reviewVerdict) || "approved_with_minor_issues".equals(reviewVerdict)) {
                    reviewApproved = true;
                    log.info("Review approved (verdict={}): runId={}, correlationId={}",
                            reviewVerdict, runId, CorrelationIdHolder.getCorrelationId());
                } else {
                    // Review rejected — loop back to developer
                    reviewDeveloperLoops++;
                    metrics.recordReviewDeveloperLoopBack();
                    logTransition(runId, "REVIEW", "DEVELOPER", "loop_back",
                            "Review verdict: " + reviewVerdict + " (iteration " + reviewDeveloperLoops + "/"
                                    + MAX_REVIEW_DEVELOPER_LOOPS + ")");

                    if (reviewDeveloperLoops >= MAX_REVIEW_DEVELOPER_LOOPS) {
                        log.warn("Review-Developer loop limit exceeded: runId={}, iterations={}, correlationId={}",
                                runId, reviewDeveloperLoops, CorrelationIdHolder.getCorrelationId());
                        throw new EscalationException(buildLoopEscalation(
                                "Review-Developer loop exceeded " + MAX_REVIEW_DEVELOPER_LOOPS + " iterations",
                                "Review continues to find critical/high issues after " + reviewDeveloperLoops
                                        + " fix attempts",
                                reviewVerdict));
                    }

                    log.info("Review loop-back to developer: runId={}, iteration={}/{}, verdict={}, correlationId={}",
                            runId, reviewDeveloperLoops, MAX_REVIEW_DEVELOPER_LOOPS, reviewVerdict,
                            CorrelationIdHolder.getCorrelationId());
                    emitStatus(runId, "IN_PROGRESS", "DEVELOPER", progressPercent(3));
                    executeDeveloperStep(context);
                }
            }

            // Tester loop: review approved → tester → (writer | developer)
            boolean testsGreen = runEntity.isStepPruned("TESTER");
            if (testsGreen) {
                log.info("TESTER step pruned by user: runId={}", runId);
            }
            while (!testsGreen) {
                emitStatus(runId, "IN_PROGRESS", "TESTER", progressPercent(5));
                String ciStatus = executeTesterStepWithStatus(context);

                if ("GREEN".equals(ciStatus)) {
                    testsGreen = true;
                    log.info("Tests passed (GREEN): runId={}, correlationId={}",
                            runId, CorrelationIdHolder.getCorrelationId());
                } else {
                    // Tests failed — loop back to developer
                    testerDeveloperLoops++;
                    metrics.recordTesterDeveloperLoopBack();
                    logTransition(runId, "TESTER", "DEVELOPER", "loop_back",
                            "CI status: " + ciStatus + " (iteration " + testerDeveloperLoops + "/"
                                    + MAX_TESTER_DEVELOPER_LOOPS + ")");

                    if (testerDeveloperLoops >= MAX_TESTER_DEVELOPER_LOOPS) {
                        log.warn("Tester-Developer loop limit exceeded: runId={}, iterations={}, correlationId={}",
                                runId, testerDeveloperLoops, CorrelationIdHolder.getCorrelationId());
                        throw new EscalationException(buildLoopEscalation(
                                "Tester-Developer loop exceeded " + MAX_TESTER_DEVELOPER_LOOPS + " iterations",
                                "Tests continue to fail after " + testerDeveloperLoops + " fix attempts",
                                ciStatus));
                    }

                    log.info("Tester loop-back to developer: runId={}, iteration={}/{}, ciStatus={}, correlationId={}",
                            runId, testerDeveloperLoops, MAX_TESTER_DEVELOPER_LOOPS, ciStatus,
                            CorrelationIdHolder.getCorrelationId());
                    emitStatus(runId, "IN_PROGRESS", "DEVELOPER", progressPercent(3));
                    executeDeveloperStep(context);

                    // After developer fix, re-enter review loop
                    emitStatus(runId, "IN_PROGRESS", "REVIEW", progressPercent(4));
                    String reReviewVerdict = executePersonaReviewWithVerdict(context);
                    if (!"approved".equals(reReviewVerdict)
                            && !"approved_with_minor_issues".equals(reReviewVerdict)) {
                        reviewDeveloperLoops++;
                        if (reviewDeveloperLoops >= MAX_REVIEW_DEVELOPER_LOOPS) {
                            throw new EscalationException(buildLoopEscalation(
                                    "Review-Developer loop exceeded " + MAX_REVIEW_DEVELOPER_LOOPS
                                            + " iterations (during tester loop-back)",
                                    "Review rejected during tester-developer loop-back",
                                    reReviewVerdict));
                        }
                    }
                }
            }

            // --- Pre-Merge Quality Gate: Judge (LLM-as-a-Judge) with Majority Voting ---
            log.info("Pre-merge Judge evaluation (majority voting): runId={}, correlationId={}",
                    runId, CorrelationIdHolder.getCorrelationId());
            JudgeService.JudgeVerdict preMergeVerdict = judgeService.evaluateWithMajorityVoting(
                    runEntity, "pre_merge", "persona_review_report");
            metrics.recordJudgeEvaluation(preMergeVerdict.verdict());
            metrics.recordVotingExecution("pre_merge");

            if (preMergeVerdict.isVeto()) {
                log.warn("Judge VETO at pre-merge gate: score={}, runId={}, correlationId={}",
                        preMergeVerdict.overallScore(), runId, CorrelationIdHolder.getCorrelationId());
                throw new EscalationException(buildLoopEscalation(
                        "Judge vetoed at pre-merge quality gate (score=" +
                                String.format("%.2f", preMergeVerdict.overallScore()) + ")",
                        "Quality score below threshold: " + preMergeVerdict.recommendation(),
                        preMergeVerdict.verdict()));
            }

            if ("conditional_pass".equals(preMergeVerdict.verdict())) {
                log.warn("Judge CONDITIONAL PASS at pre-merge gate: score={}, runId={}, correlationId={}",
                        preMergeVerdict.overallScore(), runId, CorrelationIdHolder.getCorrelationId());
                emitAndTrace(runId, new WorkflowEvent.WorkflowStatusUpdate(
                        runId, Instant.now(),
                        "CONDITIONAL_PASS: Quality score " + String.format("%.2f", preMergeVerdict.overallScore()) +
                                " — " + preMergeVerdict.recommendation(),
                        "JUDGE", progressPercent(5)));
            }

            // --- Terminal: Writer ---
            if (!runEntity.isStepPruned("WRITER")) {
                emitStatus(runId, "IN_PROGRESS", "WRITER", progressPercent(6));
                executeWriterStep(context);
            } else {
                log.info("WRITER step pruned by user: runId={}", runId);
            }
            executeGraftsAfter(runId, "WRITER", context, runEntity);

            runEntity.setStatus(RunStatus.DONE);
            runEntity.setCurrentAgent(null);
            runEntity.setEnvironmentLifecycle(EnvironmentLifecycle.COMPLETED);
            runRepository.save(runEntity);

            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.getWorkflowDuration());
            metrics.recordWorkflowSuccess(duration);

            emitStatus(runId, "DONE", null, 100.0);
            blackboardService.cleanup(runId);
            traceEventService.cleanup(runId);
            eventBus.completeEmitters(runId);

            log.info("Workflow completed successfully: runId={}, duration={}ms, correlationId={}",
                    runEntity.getId(), duration, CorrelationIdHolder.getCorrelationId());

        } catch (EscalationException e) {
            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.getWorkflowDuration());
            metrics.recordWorkflowEscalation(duration);
            handleEscalation(runEntity, e);
            emitAndTrace(runId, new WorkflowEvent.EscalationRaised(
                    runId, Instant.now(), runEntity.getCurrentAgent(), e.getMessage()));
            traceEventService.cleanup(runId);
            eventBus.completeEmitters(runId);
        } catch (OrchestratorException e) {
            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.getWorkflowDuration());
            metrics.recordWorkflowFailure(duration);
            handleOrchestratorException(runEntity, e);
            emitAndTrace(runId, new WorkflowEvent.WorkflowError(
                    runId, Instant.now(), runEntity.getCurrentAgent(), e.getErrorCode(), e.getMessage()));
            traceEventService.cleanup(runId);
            eventBus.completeEmitters(runId);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            workflowTimer.stop(metrics.getWorkflowDuration());
            metrics.recordWorkflowFailure(duration);
            handleFailure(runEntity, e);
            emitAndTrace(runId, new WorkflowEvent.WorkflowError(
                    runId, Instant.now(), runEntity.getCurrentAgent(),
                    e.getClass().getSimpleName(), e.getMessage()));
            traceEventService.cleanup(runId);
            eventBus.completeEmitters(runId);
        }
    }

    private void executePmStep(RunContext context) throws Exception {
        String role = "PM";
        UUID runId = context.getRunEntity().getId();
        CorrelationIdHolder.setAgentName(role);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();

        emitAndTrace(runId, new WorkflowEvent.StepStart(runId, Instant.now(), role, "execute"));

        Set<String> caps = Set.of("ticket_analysis", "requirement_extraction");
        AgentCard card = agentStepFactory.getActiveCard(role);
        log.info("Selected agent: {} for role {}: runId={}", card != null ? card.name() : "local", role, runId);
        AgentBinding binding = card != null
                ? agentBindingService.negotiate(card, caps, runId) : null;

        try {
            log.info("Executing PM step: runId={}, correlationId={}",
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());

            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.PM);
            runEntity.setCurrentAgent(role);
            runRepository.save(runEntity);

            String artifact = agentStepFactory.resolveForRole(role, caps).execute(context);
            schemaValidator.validate(artifact, "ticket_plan.schema.json");
            emitAndTrace(runId, new WorkflowEvent.SchemaValidation(
                    runId, Instant.now(), role, "ticket_plan.schema.json", true));

            context.setTicketPlan(artifact);
            persistArtifact(runEntity, role, "ticket_plan.json", artifact);
            snapshotEnvironment(context);

            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(role, "execute", duration);

            emitAndTrace(runId, new WorkflowEvent.StepComplete(
                    runId, Instant.now(), role, duration, "ticket_plan.json"));

            if (binding != null) agentBindingService.revokeBinding(binding.bindingId(), "step_completed");

            log.info("PM step completed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepError(role, "execute");

            log.error("PM step failed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void executeQualifierStep(RunContext context) throws Exception {
        String role = "QUALIFIER";
        UUID runId = context.getRunEntity().getId();
        CorrelationIdHolder.setAgentName(role);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();

        emitAndTrace(runId, new WorkflowEvent.StepStart(runId, Instant.now(), role, "execute"));

        Set<String> caps = Set.of("work_estimation", "task_decomposition");
        AgentCard card = agentStepFactory.getActiveCard(role);
        log.info("Selected agent: {} for role {}: runId={}", card != null ? card.name() : "local", role, runId);
        AgentBinding binding = card != null
                ? agentBindingService.negotiate(card, caps, runId) : null;

        try {
            log.info("Executing Qualifier step: runId={}, correlationId={}",
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());

            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.QUALIFIER);
            runEntity.setCurrentAgent(role);
            runRepository.save(runEntity);

            String artifact = agentStepFactory.resolveForRole(role, caps).execute(context);
            schemaValidator.validate(artifact, "work_plan.schema.json");
            emitAndTrace(runId, new WorkflowEvent.SchemaValidation(
                    runId, Instant.now(), role, "work_plan.schema.json", true));

            context.setWorkPlan(artifact);
            persistArtifact(runEntity, role, "work_plan.json", artifact);
            snapshotEnvironment(context);

            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(role, "execute", duration);

            emitAndTrace(runId, new WorkflowEvent.StepComplete(
                    runId, Instant.now(), role, duration, "work_plan.json"));

            if (binding != null) agentBindingService.revokeBinding(binding.bindingId(), "step_completed");

            log.info("Qualifier step completed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepError(role, "execute");

            log.error("Qualifier step failed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void executeArchitectStep(RunContext context) throws Exception {
        String role = "ARCHITECT";
        UUID runId = context.getRunEntity().getId();
        CorrelationIdHolder.setAgentName(role);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();

        emitAndTrace(runId, new WorkflowEvent.StepStart(runId, Instant.now(), role, "execute"));

        Set<String> caps = Set.of("system_design", "architecture_planning");
        AgentCard card = agentStepFactory.getActiveCard(role);
        log.info("Selected agent: {} for role {}: runId={}", card != null ? card.name() : "local", role, runId);
        AgentBinding binding = card != null
                ? agentBindingService.negotiate(card, caps, runId) : null;

        try {
            log.info("Executing Architect step: runId={}, correlationId={}",
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());

            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.ARCHITECT);
            runEntity.setCurrentAgent(role);
            runRepository.save(runEntity);

            String artifact = agentStepFactory.resolveForRole(role, caps).execute(context);
            context.setArchitectureNotes(artifact);
            persistArtifact(runEntity, role, "architecture_notes.md", artifact);
            snapshotEnvironment(context);

            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(role, "execute", duration);

            emitAndTrace(runId, new WorkflowEvent.StepComplete(
                    runId, Instant.now(), role, duration, "architecture_notes.md"));

            if (binding != null) agentBindingService.revokeBinding(binding.bindingId(), "step_completed");

            log.info("Architect step completed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepError(role, "execute");

            log.error("Architect step failed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void executeDeveloperStep(RunContext context) throws Exception {
        String agentName = "DEVELOPER";
        UUID runId = context.getRunEntity().getId();
        CorrelationIdHolder.setAgentName(agentName);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();

        emitAndTrace(runId, new WorkflowEvent.StepStart(runId, Instant.now(), agentName, "generate"));

        try {
            log.info("Executing Developer step (code generation only): runId={}, correlationId={}",
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());

            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.DEVELOPER);
            runEntity.setCurrentAgent(agentName);
            runRepository.save(runEntity);

            // Dynamic interrupt check before code generation
            checkInterrupt(agentName, "generate", runId);

            developerStep.generateCode(context);
            snapshotEnvironment(context);

            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(agentName, "generate", duration);

            emitAndTrace(runId, new WorkflowEvent.StepComplete(
                    runId, Instant.now(), agentName, duration, "code_changes"));

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
        UUID runId = context.getRunEntity().getId();
        CorrelationIdHolder.setAgentName(agentName);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();

        emitAndTrace(runId, new WorkflowEvent.StepStart(runId, Instant.now(), agentName, "execute"));

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
            emitAndTrace(runId, new WorkflowEvent.SchemaValidation(
                    runId, Instant.now(), agentName, "persona_review.schema.json", true));

            persistArtifact(runEntity, agentName, "persona_review_report", reportJson);

            // Dynamic interrupt check before commit & PR creation
            checkInterrupt("DEVELOPER", "commitAndCreatePullRequest", runId);

            String prUrl = developerStep.commitAndCreatePullRequest(context, codeChanges);
            persistArtifact(runEntity, "DEVELOPER", "pr_url", prUrl);

            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(agentName, "execute", duration);

            emitAndTrace(runId, new WorkflowEvent.StepComplete(
                    runId, Instant.now(), agentName, duration, "persona_review_report"));

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
        String role = "TESTER";
        UUID runId = context.getRunEntity().getId();
        CorrelationIdHolder.setAgentName(role);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();

        emitAndTrace(runId, new WorkflowEvent.StepStart(runId, Instant.now(), role, "execute"));

        Set<String> caps = Set.of("ci_validation", "test_generation");
        AgentCard card = agentStepFactory.getActiveCard(role);
        log.info("Selected agent: {} for role {}: runId={}", card != null ? card.name() : "local", role, runId);
        AgentBinding binding = card != null
                ? agentBindingService.negotiate(card, caps, runId) : null;

        try {
            log.info("Executing Tester step: runId={}, correlationId={}",
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());

            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.TESTER);
            runEntity.setCurrentAgent(role);
            runRepository.save(runEntity);

            String artifact = agentStepFactory.resolveForRole(role, caps).execute(context);
            schemaValidator.validate(artifact, "test_report.schema.json");
            emitAndTrace(runId, new WorkflowEvent.SchemaValidation(
                    runId, Instant.now(), role, "test_report.schema.json", true));

            persistArtifact(runEntity, role, "test_report.json", artifact);
            snapshotEnvironment(context);

            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(role, "execute", duration);

            emitAndTrace(runId, new WorkflowEvent.StepComplete(
                    runId, Instant.now(), role, duration, "test_report.json"));

            if (binding != null) agentBindingService.revokeBinding(binding.bindingId(), "step_completed");

            log.info("Tester step completed: runId={}, duration={}ms, ciFixCount={}, e2eFixCount={}, correlationId={}",
                    context.getRunEntity().getId(), duration, runEntity.getCiFixCount(),
                    runEntity.getE2eFixCount(), CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepError(role, "execute");

            log.error("Tester step failed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    private void executeWriterStep(RunContext context) throws Exception {
        String role = "WRITER";
        UUID runId = context.getRunEntity().getId();
        CorrelationIdHolder.setAgentName(role);
        Timer.Sample stepTimer = metrics.startAgentStepTimer();
        long startTime = System.currentTimeMillis();

        emitAndTrace(runId, new WorkflowEvent.StepStart(runId, Instant.now(), role, "execute"));

        Set<String> caps = Set.of("documentation", "changelog");
        AgentCard card = agentStepFactory.getActiveCard(role);
        log.info("Selected agent: {} for role {}: runId={}", card != null ? card.name() : "local", role, runId);
        AgentBinding binding = card != null
                ? agentBindingService.negotiate(card, caps, runId) : null;

        try {
            log.info("Executing Writer step: runId={}, correlationId={}",
                    context.getRunEntity().getId(), CorrelationIdHolder.getCorrelationId());

            RunEntity runEntity = context.getRunEntity();
            runEntity.setStatus(RunStatus.WRITER);
            runEntity.setCurrentAgent(role);
            runRepository.save(runEntity);

            // Dynamic interrupt check before writer execution
            checkInterrupt(role, "execute", runId);

            String artifact = agentStepFactory.resolveForRole(role, caps).execute(context);
            persistArtifact(runEntity, role, "docs_update", artifact);
            snapshotEnvironment(context);

            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepExecution(role, "execute", duration);

            emitAndTrace(runId, new WorkflowEvent.StepComplete(
                    runId, Instant.now(), role, duration, "docs_update"));

            if (binding != null) agentBindingService.revokeBinding(binding.bindingId(), "step_completed");

            log.info("Writer step completed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            stepTimer.stop(metrics.getAgentStepDuration());
            metrics.recordAgentStepError(role, "execute");

            log.error("Writer step failed: runId={}, duration={}ms, correlationId={}",
                    context.getRunEntity().getId(), duration, CorrelationIdHolder.getCorrelationId(), e);
            throw e;
        } finally {
            CorrelationIdHolder.setAgentName(null);
        }
    }

    /**
     * Executes the persona review step and returns the review verdict string.
     * Extracts the status from the serialized persona_review.json.
     */
    private String executePersonaReviewWithVerdict(RunContext context) throws Exception {
        executePersonaReview(context);

        // Extract verdict from the persisted review artifact
        try {
            RunEntity runEntity = context.getRunEntity();
            return runEntity.getArtifacts().stream()
                    .filter(a -> "persona_review_report".equals(a.getArtifactType()))
                    .reduce((first, second) -> second) // last one (most recent)
                    .map(a -> {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(a.getPayload());
                            return root.path("overallAssessment").path("status").asText("approved");
                        } catch (Exception e) {
                            log.warn("Failed to parse review verdict, defaulting to approved: runId={}", runEntity.getId(), e);
                            return "approved";
                        }
                    })
                    .orElse("approved");
        } catch (Exception e) {
            log.warn("Failed to extract review verdict, defaulting to approved: correlationId={}",
                    CorrelationIdHolder.getCorrelationId(), e);
            return "approved";
        }
    }

    /**
     * Executes the tester step and returns the CI status (GREEN or RED).
     * Extracts the ciStatus from the serialized test_report.json.
     */
    private String executeTesterStepWithStatus(RunContext context) throws Exception {
        executeTesterStep(context);

        try {
            RunEntity runEntity = context.getRunEntity();
            return runEntity.getArtifacts().stream()
                    .filter(a -> "test_report.json".equals(a.getArtifactType()))
                    .reduce((first, second) -> second)
                    .map(a -> {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(a.getPayload());
                            return root.path("ciStatus").asText("RED");
                        } catch (Exception e) {
                            log.warn("Failed to parse CI status, defaulting to RED: runId={}", runEntity.getId(), e);
                            return "RED";
                        }
                    })
                    .orElse("RED");
        } catch (Exception e) {
            log.warn("Failed to extract CI status, defaulting to RED: correlationId={}",
                    CorrelationIdHolder.getCorrelationId(), e);
            return "RED";
        }
    }

    /**
     * Logs a state machine transition for audit trail.
     */
    private void logTransition(UUID runId, String from, String to, String type, String reason) {
        log.info("State transition: runId={}, from={}, to={}, type={}, reason={}, correlationId={}",
                runId, from, to, type, reason, CorrelationIdHolder.getCorrelationId());

        emitAndTrace(runId, new WorkflowEvent.WorkflowStatusUpdate(
                runId, Instant.now(), type.toUpperCase() + ": " + from + " → " + to, to, progressPercent(3)));
    }

    /**
     * Builds a structured escalation JSON when a loop limit is exceeded.
     */
    private String buildLoopEscalation(String context, String blocker, String lastStatus) {
        return String.format("""
                {
                  "context": "%s",
                  "blocker": "%s",
                  "options": [
                    {
                      "name": "Manual fix",
                      "pros": ["Human expertise can resolve complex issues"],
                      "cons": ["Requires developer time"],
                      "risk": "Delays delivery"
                    },
                    {
                      "name": "Accept as-is",
                      "pros": ["Unblocks pipeline"],
                      "cons": ["Known issues remain"],
                      "risk": "Technical debt or potential defects in production"
                    }
                  ],
                  "recommendation": "Manual fix — a human should review the remaining issues and apply targeted fixes",
                  "decisionNeeded": "Should a human developer fix the remaining issues, or should the current state be accepted?",
                  "evidence": ["Last status: %s", "Loop limit exceeded — autonomous resolution attempts exhausted"]
                }""", context, blocker, lastStatus);
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
        runEntity.setEnvironmentLifecycle(EnvironmentLifecycle.PAUSED);
        runRepository.save(runEntity);
    }

    private void handleOrchestratorException(RunEntity runEntity, OrchestratorException e) {
        log.error(
                "Workflow failed with orchestrator exception: runId={}, errorCode={}, recoveryStrategy={}, retryable={}, correlationId={}",
                runEntity.getId(), e.getErrorCode(), e.getRecoveryStrategy(), e.isRetryable(),
                CorrelationIdHolder.getCorrelationId(), e);

        runEntity.setStatus(RunStatus.FAILED);
        runEntity.setEnvironmentLifecycle(EnvironmentLifecycle.PAUSED);
        runRepository.save(runEntity);

        persistErrorArtifact(runEntity, e);
    }

    private void handleFailure(RunEntity runEntity, Exception e) {
        log.error("Workflow failed with unexpected exception: runId={}, agent={}, correlationId={}",
                runEntity.getId(), runEntity.getCurrentAgent(), CorrelationIdHolder.getCorrelationId(), e);

        runEntity.setStatus(RunStatus.FAILED);
        runEntity.setEnvironmentLifecycle(EnvironmentLifecycle.PAUSED);
        runRepository.save(runEntity);

        persistErrorArtifact(runEntity, e);
    }

    // -------------------------------------------------------------------------
    // TEA Environment Snapshot / Restore
    // -------------------------------------------------------------------------

    /**
     * Serialize the current RunContext state into the RunEntity's environmentCheckpoint.
     * Called after each successful step so the environment is always recoverable.
     */
    private void snapshotEnvironment(RunContext context) {
        try {
            EnvironmentSnapshot snapshot = EnvironmentSnapshot.of(context);
            String json = objectMapper.writeValueAsString(snapshot);
            RunEntity runEntity = context.getRunEntity();
            runEntity.setEnvironmentCheckpoint(json);
            runEntity.setEnvironmentLifecycle(EnvironmentLifecycle.ACTIVE);
            runRepository.save(runEntity);
            log.debug("TEA SNAPSHOT: runId={}, capturedAt={}", runEntity.getId(), snapshot.capturedAt());
        } catch (Exception e) {
            log.error("TEA SNAPSHOT failed: runId={}", context.getRunEntity().getId(), e);
        }
    }

    /**
     * Restore a RunContext from the persisted environment checkpoint.
     * Used by the resume endpoint to reconstruct state without re-executing steps.
     *
     * @param runId the run to restore
     * @return a RunContext populated from the latest checkpoint, or null if no checkpoint
     */
    public RunContext restoreEnvironment(UUID runId) {
        RunEntity entity = runRepository.findById(runId).orElse(null);
        if (entity == null) {
            log.warn("TEA RESTORE: run not found: {}", runId);
            return null;
        }
        String checkpoint = entity.getEnvironmentCheckpoint();
        if (checkpoint == null || checkpoint.isBlank()) {
            log.warn("TEA RESTORE: no checkpoint for runId={}", runId);
            String[] repoParts = entity.getRepo().split("/");
            return new RunContext(entity, repoParts[0], repoParts[1]);
        }
        try {
            EnvironmentSnapshot snapshot = objectMapper.readValue(checkpoint, EnvironmentSnapshot.class);
            RunContext ctx = snapshot.restore(entity);
            log.info("TEA RESTORE: runId={}, capturedAt={}", runId, snapshot.capturedAt());
            return ctx;
        } catch (Exception e) {
            log.error("TEA RESTORE failed: runId={}", runId, e);
            String[] repoParts = entity.getRepo().split("/");
            return new RunContext(entity, repoParts[0], repoParts[1]);
        }
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

    private void emitStatus(UUID runId, String status, String currentAgent, double progress) {
        WorkflowEvent.WorkflowStatusUpdate event = new WorkflowEvent.WorkflowStatusUpdate(
                runId, Instant.now(), status, currentAgent, progress);
        eventBus.emit(runId, event);
        traceEventService.recordEvent(event);
    }

    private double progressPercent(int completedSteps) {
        return (completedSteps * 100.0) / TOTAL_STEPS;
    }

    private void emitAndTrace(UUID runId, WorkflowEvent event) {
        eventBus.emit(runId, event);
        traceEventService.recordEvent(event);
    }

    /**
     * Check the DynamicInterruptService before a high-risk operation.
     * Blocks if the interrupt decision is BLOCK; logs warning if requires human approval.
     */
    private void checkInterrupt(String agentName, String action, UUID runId) throws EscalationException {
        DynamicInterruptService.InterruptDecision decision = interruptService.evaluate(
                agentName, null, action, Map.of(), runId);

        if (decision.isBlocked()) {
            log.error("Interrupt BLOCKED: agent={}, action={}, rule={}, runId={}",
                    agentName, action, decision.getRuleName(), runId);
            throw new EscalationException(buildLoopEscalation(
                    "Dynamic interrupt blocked action: " + action,
                    "Rule '" + decision.getRuleName() + "': " + decision.getMessage(),
                    "BLOCKED"));
        }

        if (decision.requiresHumanApproval()) {
            log.warn("Interrupt requires human approval: agent={}, action={}, rule={}, tier={}, runId={}",
                    agentName, action, decision.getRuleName(), decision.getTier(), runId);
            emitAndTrace(runId, new WorkflowEvent.WorkflowStatusUpdate(
                    runId, Instant.now(),
                    "INTERRUPT_APPROVAL_NEEDED: " + decision.getMessage(),
                    agentName, -1));
        }
    }

    // -------------------------------------------------------------------------
    // Vis-CoT Graft Execution
    // -------------------------------------------------------------------------

    private static final java.util.Map<String, String> AGENT_NAME_TO_ROLE = java.util.Map.of(
            "pm-v1",        "PM",
            "qualifier-v1", "QUALIFIER",
            "architect-v1", "ARCHITECT",
            "tester-v1",    "TESTER",
            "writer-v1",    "WRITER"
    );

    /**
     * After each pipeline step, execute any pending grafts whose "after" field matches
     * the completed step. Grafts are resolved via AgentStepFactory and run inline.
     * Executed grafts are removed from the pending list and saved.
     */
    private void executeGraftsAfter(UUID runId, String completedStep, RunContext context, RunEntity runEntity) {
        String raw = runEntity.getPendingGrafts();
        if (raw == null || raw.isBlank() || raw.equals("[]")) return;

        try {
            com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(raw);
            java.util.List<com.fasterxml.jackson.databind.JsonNode> remaining = new java.util.ArrayList<>();
            boolean anyExecuted = false;

            for (com.fasterxml.jackson.databind.JsonNode graft : arr) {
                String after     = graft.path("after").asText("");
                String agentName = graft.path("agentName").asText("");

                if (!completedStep.equalsIgnoreCase(after)) {
                    remaining.add(graft);
                    continue;
                }

                String role = AGENT_NAME_TO_ROLE.get(agentName);
                if (role == null) {
                    log.warn("GRAFT: unknown agentName='{}' for graft after={}, skipping: runId={}", agentName, after, runId);
                    remaining.add(graft);
                    continue;
                }

                log.info("GRAFT: executing agentName={} role={} after={}: runId={}", agentName, role, after, runId);
                emitAndTrace(runId, new WorkflowEvent.WorkflowStatusUpdate(
                        runId, Instant.now(), "GRAFT: " + agentName + " (after " + after + ")", role, -1));

                try {
                    agentStepFactory.resolveForRole(role, Set.of()).execute(context);
                    anyExecuted = true;
                    log.info("GRAFT: completed agentName={}: runId={}", agentName, runId);
                } catch (Exception e) {
                    log.error("GRAFT: execution failed for agentName={}: runId={}", agentName, runId, e);
                    remaining.add(graft); // keep it for retry
                }
            }

            if (anyExecuted) {
                String updatedJson = remaining.isEmpty() ? "[]" : objectMapper.writeValueAsString(remaining);
                runEntity.setPendingGrafts(updatedJson);
                runRepository.save(runEntity);
            }
        } catch (Exception e) {
            log.error("GRAFT: failed to process pending grafts: runId={}", runId, e);
        }
    }
}
