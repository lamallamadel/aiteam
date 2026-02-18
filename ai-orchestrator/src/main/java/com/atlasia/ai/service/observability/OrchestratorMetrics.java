package com.atlasia.ai.service.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class OrchestratorMetrics {
    private final MeterRegistry meterRegistry;

    private final Counter githubApiCallsTotal;
    private final Counter githubApiErrorsTotal;
    private final Counter githubApiRateLimitTotal;
    private final Timer githubApiDuration;

    private final Counter llmCallsTotal;
    private final Counter llmErrorsTotal;
    private final Timer llmDuration;
    private final Counter llmTokensUsed;

    private final Counter agentStepExecutionsTotal;
    private final Counter agentStepErrorsTotal;
    private final Timer agentStepDuration;

    private final Counter workflowExecutionsTotal;
    private final Counter workflowSuccessTotal;
    private final Counter workflowFailureTotal;
    private final Counter workflowEscalationTotal;
    private final Timer workflowDuration;

    private final Counter ciFixAttemptsTotal;
    private final Counter e2eFixAttemptsTotal;

    private final Counter reviewDeveloperLoopBacks;
    private final Counter testerDeveloperLoopBacks;
    private final Counter guardrailViolationsTotal;
    private final Counter conflictResolutionsTotal;

    private final Timer llmSemanticLatency;
    private final Counter tokenBudgetExceeded;
    private final DistributionSummary tokenUsagePerBolt;
    private final DistributionSummary costPerBolt;

    private final Counter judgeEvaluationsTotal;
    private final Counter judgeVetoTotal;
    private final Counter judgePassTotal;
    private final Counter interruptTriggeredTotal;
    private final Counter interruptApprovedTotal;
    private final Counter interruptDeniedTotal;
    private final Counter blackboardWritesTotal;
    private final Counter blackboardReadsTotal;
    private final Counter a2aDiscoveriesTotal;
    private final Counter votingExecutionsTotal;

    public OrchestratorMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.githubApiCallsTotal = Counter.builder("orchestrator.github.api.calls.total")
                .description("Total number of GitHub API calls")
                .register(meterRegistry);

        this.githubApiErrorsTotal = Counter.builder("orchestrator.github.api.errors.total")
                .description("Total number of GitHub API errors")
                .register(meterRegistry);

        this.githubApiRateLimitTotal = Counter.builder("orchestrator.github.api.ratelimit.total")
                .description("Total number of GitHub API rate limit hits")
                .register(meterRegistry);

        this.githubApiDuration = Timer.builder("orchestrator.github.api.duration")
                .description("Duration of GitHub API calls")
                .register(meterRegistry);

        this.llmCallsTotal = Counter.builder("orchestrator.llm.calls.total")
                .description("Total number of LLM API calls")
                .register(meterRegistry);

        this.llmErrorsTotal = Counter.builder("orchestrator.llm.errors.total")
                .description("Total number of LLM API errors")
                .register(meterRegistry);

        this.llmDuration = Timer.builder("orchestrator.llm.duration")
                .description("Duration of LLM API calls")
                .register(meterRegistry);

        this.llmTokensUsed = Counter.builder("orchestrator.llm.tokens.used")
                .description("Total number of LLM tokens used")
                .register(meterRegistry);

        this.agentStepExecutionsTotal = Counter.builder("orchestrator.agent.step.executions.total")
                .description("Total number of agent step executions")
                .register(meterRegistry);

        this.agentStepErrorsTotal = Counter.builder("orchestrator.agent.step.errors.total")
                .description("Total number of agent step errors")
                .register(meterRegistry);

        this.agentStepDuration = Timer.builder("orchestrator.agent.step.duration")
                .description("Duration of agent step executions")
                .register(meterRegistry);

        this.workflowExecutionsTotal = Counter.builder("orchestrator.workflow.executions.total")
                .description("Total number of workflow executions")
                .register(meterRegistry);

        this.workflowSuccessTotal = Counter.builder("orchestrator.workflow.success.total")
                .description("Total number of successful workflows")
                .register(meterRegistry);

        this.workflowFailureTotal = Counter.builder("orchestrator.workflow.failure.total")
                .description("Total number of failed workflows")
                .register(meterRegistry);

        this.workflowEscalationTotal = Counter.builder("orchestrator.workflow.escalation.total")
                .description("Total number of escalated workflows")
                .register(meterRegistry);

        this.workflowDuration = Timer.builder("orchestrator.workflow.duration")
                .description("Duration of workflow executions")
                .register(meterRegistry);

        this.ciFixAttemptsTotal = Counter.builder("orchestrator.ci.fix.attempts.total")
                .description("Total number of CI fix attempts")
                .register(meterRegistry);

        this.e2eFixAttemptsTotal = Counter.builder("orchestrator.e2e.fix.attempts.total")
                .description("Total number of E2E fix attempts")
                .register(meterRegistry);

        this.llmSemanticLatency = Timer.builder("orchestrator.llm.semantic.latency")
                .description("LLM semantic latency — time from prompt submission to first meaningful response")
                .register(meterRegistry);

        this.tokenBudgetExceeded = Counter.builder("orchestrator.token.budget.exceeded")
                .description("Number of times an agent exceeded its token budget")
                .register(meterRegistry);

        this.tokenUsagePerBolt = DistributionSummary.builder("orchestrator.token.usage.per.bolt")
                .description("Token usage distribution per bolt execution")
                .register(meterRegistry);

        this.reviewDeveloperLoopBacks = Counter.builder("orchestrator.loop.review.developer.total")
                .description("Total number of review-to-developer loop-backs")
                .register(meterRegistry);

        this.testerDeveloperLoopBacks = Counter.builder("orchestrator.loop.tester.developer.total")
                .description("Total number of tester-to-developer loop-backs")
                .register(meterRegistry);

        this.guardrailViolationsTotal = Counter.builder("orchestrator.guardrail.violations.total")
                .description("Total number of security guardrail violations detected")
                .register(meterRegistry);

        this.conflictResolutionsTotal = Counter.builder("orchestrator.conflict.resolutions.total")
                .description("Total number of reviewer conflict resolutions")
                .register(meterRegistry);

        this.costPerBolt = DistributionSummary.builder("orchestrator.cost.per.bolt")
                .description("Estimated cost in USD per bolt execution")
                .baseUnit("USD")
                .register(meterRegistry);

        this.judgeEvaluationsTotal = Counter.builder("orchestrator.judge.evaluations.total")
                .description("Total number of Judge evaluations executed")
                .register(meterRegistry);

        this.judgeVetoTotal = Counter.builder("orchestrator.judge.veto.total")
                .description("Total number of Judge vetoes issued")
                .register(meterRegistry);

        this.judgePassTotal = Counter.builder("orchestrator.judge.pass.total")
                .description("Total number of Judge pass verdicts")
                .register(meterRegistry);

        this.interruptTriggeredTotal = Counter.builder("orchestrator.interrupt.triggered.total")
                .description("Total number of dynamic interrupts triggered")
                .register(meterRegistry);

        this.interruptApprovedTotal = Counter.builder("orchestrator.interrupt.approved.total")
                .description("Total number of dynamic interrupts approved by human")
                .register(meterRegistry);

        this.interruptDeniedTotal = Counter.builder("orchestrator.interrupt.denied.total")
                .description("Total number of dynamic interrupts denied by human")
                .register(meterRegistry);

        this.blackboardWritesTotal = Counter.builder("orchestrator.blackboard.writes.total")
                .description("Total number of blackboard write operations")
                .register(meterRegistry);

        this.blackboardReadsTotal = Counter.builder("orchestrator.blackboard.reads.total")
                .description("Total number of blackboard read operations")
                .register(meterRegistry);

        this.a2aDiscoveriesTotal = Counter.builder("orchestrator.a2a.discoveries.total")
                .description("Total number of A2A agent discovery queries")
                .register(meterRegistry);

        this.votingExecutionsTotal = Counter.builder("orchestrator.voting.executions.total")
                .description("Total number of majority voting executions")
                .register(meterRegistry);
    }

    public void recordGitHubApiCall(String endpoint, long duration) {
        githubApiCallsTotal.increment();
        if (duration > 0) {
            githubApiDuration.record(duration, TimeUnit.MILLISECONDS);
        }
    }

    public void recordGitHubApiError(String endpoint, String errorType) {
        githubApiErrorsTotal.increment();
    }

    public void recordGitHubApiRateLimit(String endpoint) {
        githubApiRateLimitTotal.increment();
    }

    public Timer.Sample startGitHubApiTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordLlmCall(String model, long duration, int tokensUsed) {
        llmCallsTotal.increment();
        if (duration > 0) {
            llmDuration.record(duration, TimeUnit.MILLISECONDS);
        }
        if (tokensUsed > 0) {
            llmTokensUsed.increment(tokensUsed);
        }
    }

    public void recordLlmError(String model, String errorType) {
        llmErrorsTotal.increment();
    }

    public Timer.Sample startLlmTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordAgentStepExecution(String agentName, String stepPhase, long duration) {
        agentStepExecutionsTotal.increment();
        if (duration > 0) {
            agentStepDuration.record(duration, TimeUnit.MILLISECONDS);
        }
    }

    public void recordAgentStepError(String agentName, String stepPhase) {
        agentStepErrorsTotal.increment();
    }

    public Timer.Sample startAgentStepTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordWorkflowExecution() {
        workflowExecutionsTotal.increment();
    }

    public void recordWorkflowSuccess(long duration) {
        workflowSuccessTotal.increment();
        if (duration > 0) {
            workflowDuration.record(duration, TimeUnit.MILLISECONDS);
        }
    }

    public void recordWorkflowFailure(long duration) {
        workflowFailureTotal.increment();
        if (duration > 0) {
            workflowDuration.record(duration, TimeUnit.MILLISECONDS);
        }
    }

    public void recordWorkflowEscalation(long duration) {
        workflowEscalationTotal.increment();
        if (duration > 0) {
            workflowDuration.record(duration, TimeUnit.MILLISECONDS);
        }
    }

    public Timer.Sample startWorkflowTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordCiFixAttempt() {
        ciFixAttemptsTotal.increment();
    }

    public void recordE2eFixAttempt() {
        e2eFixAttemptsTotal.increment();
    }

    public Timer getGitHubApiDuration() {
        return githubApiDuration;
    }

    public Timer getLlmDuration() {
        return llmDuration;
    }

    public Timer getAgentStepDuration() {
        return agentStepDuration;
    }

    public Timer getWorkflowDuration() {
        return workflowDuration;
    }

    public void recordLlmSemanticLatency(long durationMs) {
        if (durationMs > 0) {
            llmSemanticLatency.record(durationMs, TimeUnit.MILLISECONDS);
        }
    }

    public void recordTokenBudgetExceeded(String agentName) {
        tokenBudgetExceeded.increment();
    }

    public void recordTokenUsagePerBolt(double totalTokens) {
        tokenUsagePerBolt.record(totalTokens);
    }

    public void recordReviewDeveloperLoopBack() {
        reviewDeveloperLoopBacks.increment();
    }

    public void recordTesterDeveloperLoopBack() {
        testerDeveloperLoopBacks.increment();
    }

    public void recordGuardrailViolation(String guardrailName, String agentName) {
        guardrailViolationsTotal.increment();
    }

    public void recordConflictResolution(String resolutionMethod) {
        conflictResolutionsTotal.increment();
    }

    public void recordCostPerBolt(double costUsd) {
        costPerBolt.record(costUsd);
    }

    public void recordJudgeEvaluation(String verdict) {
        judgeEvaluationsTotal.increment();
        if ("veto".equals(verdict)) {
            judgeVetoTotal.increment();
        } else if ("pass".equals(verdict)) {
            judgePassTotal.increment();
        }
    }

    public void recordInterruptTriggered(String tier, String ruleName) {
        interruptTriggeredTotal.increment();
    }

    public void recordInterruptApproved(String ruleName) {
        interruptApprovedTotal.increment();
    }

    public void recordInterruptDenied(String ruleName) {
        interruptDeniedTotal.increment();
    }

    public void recordBlackboardWrite(String entryKey, String agentName) {
        blackboardWritesTotal.increment();
    }

    public void recordBlackboardRead(String entryKey, String agentName) {
        blackboardReadsTotal.increment();
    }

    public void recordA2ADiscovery(String role) {
        a2aDiscoveriesTotal.increment();
    }

    public void recordVotingExecution(String checkpoint) {
        votingExecutionsTotal.increment();
    }
}
