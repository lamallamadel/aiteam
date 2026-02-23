package com.atlasia.ai.api.dto;

import java.util.Map;

public record TokenSummaryDto(
        long totalTokens,
        long llmCallCount,
        Map<String, Long> tokensByAgent) {
}
