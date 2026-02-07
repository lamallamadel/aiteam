package com.atlasia.ai.service;

import org.springframework.stereotype.Component;

@Component
public class ArchitectStep implements AgentStep {

    @Override
    public String execute(RunContext context) throws Exception {
        StringBuilder notes = new StringBuilder();
        notes.append("# Architecture Notes\n\n");
        notes.append("## Issue: ").append(context.getRunEntity().getIssueNumber()).append("\n\n");
        notes.append("## Overview\n");
        notes.append("This document outlines the architectural approach for implementing the requested changes.\n\n");
        notes.append("## Design Decisions\n");
        notes.append("- Follow existing layered architecture (controller → service → repository → entity)\n");
        notes.append("- Maintain separation of concerns\n");
        notes.append("- Use existing patterns and conventions\n\n");
        notes.append("## Components Affected\n");
        notes.append("- Backend: Spring Boot services and controllers\n");
        notes.append("- Frontend: Angular components\n");
        notes.append("- Database: PostgreSQL schema updates if needed\n\n");
        notes.append("## Testing Strategy\n");
        notes.append("- Unit tests for business logic\n");
        notes.append("- Integration tests for API endpoints\n");
        notes.append("- E2E tests for user workflows\n");
        
        context.setArchitectureNotes(notes.toString());
        return notes.toString();
    }
}
