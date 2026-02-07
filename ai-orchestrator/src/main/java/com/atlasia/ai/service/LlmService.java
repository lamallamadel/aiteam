package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {
    private final WebClient webClient;
    private final OrchestratorProperties.Llm llmConfig;
    private final ObjectMapper objectMapper;

    public LlmService(OrchestratorProperties properties, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.llmConfig = properties.llm();
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .baseUrl(llmConfig.endpoint())
                .defaultHeader("Authorization", "Bearer " + llmConfig.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String generateCompletion(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> requestBody = Map.of(
                "model", llmConfig.model(),
                "messages", messages
        );

        Map<String, Object> response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return extractMessageContent(response);
    }

    public String generateStructuredOutput(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> responseFormat = Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "response",
                        "strict", true,
                        "schema", jsonSchema
                )
        );

        Map<String, Object> requestBody = Map.of(
                "model", llmConfig.model(),
                "messages", messages,
                "response_format", responseFormat
        );

        Map<String, Object> response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return extractMessageContent(response);
    }

    public String generateStructuredOutput(String systemPrompt, String userPrompt, String jsonSchemaString) {
        try {
            JsonNode schemaNode = objectMapper.readTree(jsonSchemaString);
            Map<String, Object> jsonSchema = objectMapper.convertValue(schemaNode, Map.class);
            return generateStructuredOutput(systemPrompt, userPrompt, jsonSchema);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON schema", e);
        }
    }

    private String extractMessageContent(Map<String, Object> response) {
        if (response == null) {
            throw new RuntimeException("No response from LLM API");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("No choices in LLM API response");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new RuntimeException("No message in LLM API response");
        }

        String content = (String) message.get("content");
        if (content == null) {
            throw new RuntimeException("No content in LLM API response message");
        }

        return content;
    }
}
