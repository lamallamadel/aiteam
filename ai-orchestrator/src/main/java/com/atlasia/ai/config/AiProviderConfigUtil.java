package com.atlasia.ai.config;

/**
 * Helpers for {@link AiProviderProperties.ProviderConfig} copies (e.g. tier model override).
 */
public final class AiProviderConfigUtil {

    private AiProviderConfigUtil() {}

    public static AiProviderProperties.ProviderConfig withModel(
            AiProviderProperties.ProviderConfig c, String modelOverride) {
        if (c == null) {
            return null;
        }
        if (modelOverride == null || modelOverride.isBlank()) {
            return c;
        }
        return new AiProviderProperties.ProviderConfig(
                c.baseUrl(),
                c.apiKey(),
                modelOverride,
                c.type(),
                c.connectTimeout(),
                c.readTimeout(),
                c.maxTokens(),
                c.temperature(),
                c.extraHeaders());
    }
}
