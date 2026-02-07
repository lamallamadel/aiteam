package com.atlasia.ai.service.observability;

import io.micrometer.core.instrument.Counter;
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
    }
    
    public void recordGitHubApiCall(String endpoint) {
        githubApiCallsTotal.increment();
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
    
    public void recordLlmCall(String model, int tokensUsed) {
        llmCallsTotal.increment();
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
    
    public void recordAgentStepExecution(String agentName, String stepPhase) {
        agentStepExecutionsTotal.increment();
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
    
    public void recordWorkflowSuccess() {
        workflowSuccessTotal.increment();
    }
    
    public void recordWorkflowFailure() {
        workflowFailureTotal.increment();
    }
    
    public void recordWorkflowEscalation() {
        workflowEscalationTotal.increment();
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
}
