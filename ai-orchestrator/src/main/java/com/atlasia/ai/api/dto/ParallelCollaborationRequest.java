package com.atlasia.ai.api.dto;

import java.util.List;

/**
 * Request body for POST /api/collaborate/* endpoints.
 *
 * @param userId     identifies the user session
 * @param personaIds personas to call in parallel
 * @param prompt     the message sent to each persona
 */
public record ParallelCollaborationRequest(
        String userId,
        List<String> personaIds,
        String prompt
) {}
