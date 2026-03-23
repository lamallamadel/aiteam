package com.atlasia.ai.service;

import com.atlasia.ai.model.EnvironmentLifecycle;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.event.WorkflowEvent;
import com.atlasia.ai.service.event.WorkflowEventBus;
import com.atlasia.ai.service.trace.TraceEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class HitlGateService {

    public static final String GATE_ARCHITECTURE_APPROVAL = "architecture_approval";
    public static final String RESUME_AFTER_ARCHITECTURE_GATE = "AFTER_ARCHITECTURE_GATE";

    private final RunRepository runRepository;
    private final BlackboardService blackboardService;
    private final TaskLedgerBuilder taskLedgerBuilder;
    private final WorkflowEventBus eventBus;
    private final TraceEventService traceEventService;

    public HitlGateService(
            RunRepository runRepository,
            BlackboardService blackboardService,
            TaskLedgerBuilder taskLedgerBuilder,
            WorkflowEventBus eventBus,
            TraceEventService traceEventService) {
        this.runRepository = runRepository;
        this.blackboardService = blackboardService;
        this.taskLedgerBuilder = taskLedgerBuilder;
        this.eventBus = eventBus;
        this.traceEventService = traceEventService;
    }

    /**
     * YAML bypass: skip gate when architecture notes explicitly indicate no structural change.
     */
    public boolean requiresArchitectureApprovalPause(RunContext context) {
        String notes = context.getArchitectureNotes();
        if (notes != null && notes.toLowerCase().contains("no_change")) {
            return false;
        }
        return true;
    }

    /**
     * Pause after ARCHITECT only for human-in-the-loop autonomy ({@code confirm} / {@code observe}).
     * {@code autonomous} runs proceed without {@link RunStatus#WAITING_GATE} (E2E and fully autonomous pipeline).
     */
    public boolean shouldPauseAfterArchitecture(RunContext context, RunEntity run) {
        return requiresArchitectureApprovalPause(context) && requiresHitlAutonomy(run);
    }

    private static boolean requiresHitlAutonomy(RunEntity run) {
        String a = run.getAutonomy();
        return "confirm".equals(a) || "observe".equals(a);
    }

    public void finalizeArchitectureGatePause(RunEntity run, UUID runId) {
        run.setStatus(RunStatus.WAITING_GATE);
        run.setPendingHitlGate(GATE_ARCHITECTURE_APPROVAL);
        run.setEnvironmentLifecycle(EnvironmentLifecycle.PAUSED);
        runRepository.save(run);

        WorkflowEvent.GatePause evt = new WorkflowEvent.GatePause(
                runId,
                Instant.now(),
                GATE_ARCHITECTURE_APPROVAL,
                "Human approval required before implementation begins.");
        eventBus.emit(runId, evt);
        traceEventService.recordEvent(evt);
    }

    @Transactional
    public void applyGateResponse(UUID runId, String gateName, String decision, String comment) throws Exception {
        RunEntity run = runRepository.findById(runId).orElseThrow();
        if (run.getStatus() != RunStatus.WAITING_GATE
                || run.getPendingHitlGate() == null
                || !run.getPendingHitlGate().equals(gateName)) {
            throw new IllegalStateException("Run is not waiting at gate: " + gateName);
        }

        String ledger = taskLedgerBuilder.readLatest(run);
        if (ledger == null) {
            ledger = taskLedgerBuilder.initialLedger(run);
        }

        String d = decision.trim().toLowerCase();
        ledger = taskLedgerBuilder.appendGateDecision(
                ledger, gateName, "respond", decision, comment != null ? comment : "");

        if ("reject".equals(d)) {
            ledger = taskLedgerBuilder.withStatus(ledger, "aborted", "none");
            ledger = taskLedgerBuilder.withTransition(
                    ledger,
                    runId,
                    "GATE",
                    "none",
                    "abort",
                    "HITL gate rejected" + (comment != null && !comment.isBlank() ? ": " + comment : ""),
                    gateName,
                    -1);
            blackboardService.write(run, "task_ledger", "orchestrator", ledger);
            run.setPendingHitlGate(null);
            run.setWorkflowResumeFrom(null);
            run.setStatus(RunStatus.FAILED);
            run.setEnvironmentLifecycle(EnvironmentLifecycle.PAUSED);
            runRepository.save(run);
            return;
        }

        ledger = taskLedgerBuilder.withStatus(ledger, "executing", "developer");
        ledger = taskLedgerBuilder.withTransition(
                ledger,
                runId,
                "GATE",
                "DEVELOPER",
                "gate_resume",
                "HITL gate cleared — proceeding to implementation",
                gateName,
                -1);
        blackboardService.write(run, "task_ledger", "orchestrator", ledger);

        run.setPendingHitlGate(null);
        run.setWorkflowResumeFrom(RESUME_AFTER_ARCHITECTURE_GATE);
        run.setStatus(RunStatus.RECEIVED);
        run.setEnvironmentLifecycle(EnvironmentLifecycle.ACTIVE);
        runRepository.save(run);
    }
}
