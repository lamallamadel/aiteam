package com.atlasia.ai.service;

import com.atlasia.ai.config.PersonaConfig;
import com.atlasia.ai.config.PersonaConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmService llmService;
    private final PersonaConfigLoader personaConfigLoader;
    private final ConversationMemoryService memoryService;

    public ChatService(
            LlmService llmService,
            PersonaConfigLoader personaConfigLoader,
            ConversationMemoryService memoryService) {
        this.llmService = llmService;
        this.personaConfigLoader = personaConfigLoader;
        this.memoryService = memoryService;
    }

    public String chat(String userId, String personaName, String message) {
        PersonaConfig persona = personaConfigLoader.getPersonaByName(personaName);
        if (persona == null) {
            throw new IllegalArgumentException("Persona not found: " + personaName);
        }

        var history = memoryService.getContextWindow(userId, personaName);
        String historyBlock = memoryService.formatContextForPrompt(history);

        String systemPrompt = buildSystemPrompt(persona, historyBlock);

        log.debug("Chat: user={} persona={} historyTurns={}", userId, personaName, history.size());

        String reply = llmService.generateCompletion(systemPrompt, message);

        memoryService.saveTurns(userId, personaName, message, reply);

        return reply;
    }

    private String buildSystemPrompt(PersonaConfig persona, String historyBlock) {
        var sb = new StringBuilder();
        sb.append("You are ").append(persona.name()).append(", a ").append(persona.role()).append(".\n");
        sb.append("Mission: ").append(persona.mission()).append("\n");
        sb.append("Focus Areas: ").append(String.join(", ", persona.focusAreas())).append("\n");
        sb.append("You are in a direct chat mode with the user. Be helpful, concise, and stick to your unique persona and expertise.");

        if (!historyBlock.isBlank()) {
            sb.append("\n\n").append(historyBlock)
              .append("\nUse the conversation history above to maintain context. ")
              .append("Refer to prior decisions naturally without repeating them unnecessarily.");
        }

        return sb.toString();
    }
}
