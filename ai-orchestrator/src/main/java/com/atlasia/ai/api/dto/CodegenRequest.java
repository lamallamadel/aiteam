package com.atlasia.ai.api.dto;

/**
 * Request body for POST /api/codegen.
 *
 * @param userId     identifies the user session
 * @param personaId  which persona generates the code (e.g. "backend-developer")
 * @param message    the user's code generation instruction
 */
public record CodegenRequest(String userId, String personaId, String message) {}
