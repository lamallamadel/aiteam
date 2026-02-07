package com.atlasia.ai.service;

public interface AgentStep {
    String execute(RunContext context) throws Exception;
}
