package com.atlasia.ai.service;

/**
 * Result of a structured LLM call, including whether the content came from the primary
 * endpoint, the fallback endpoint, or a synthetic mock when both failed.
 */
public record LlmResult(String content, LlmResultSource source) {

    public static LlmResult primary(String content) {
        return new LlmResult(content, LlmResultSource.PRIMARY);
    }

    public static LlmResult fallback(String content) {
        return new LlmResult(content, LlmResultSource.FALLBACK);
    }

    public static LlmResult mock(String content) {
        return new LlmResult(content, LlmResultSource.MOCK);
    }

    public boolean isMock() {
        return source == LlmResultSource.MOCK;
    }
}
