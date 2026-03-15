package com.atlasia.ai.service;

import com.atlasia.ai.config.ChatPersonaLoader;
import com.atlasia.ai.config.PersonaConfig;
import com.atlasia.ai.config.PersonaConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmService llmService;
    private final ChatPersonaLoader chatPersonaLoader;
    private final PersonaConfigLoader personaConfigLoader;
    private final ConversationMemoryService memoryService;
    private final HandoffService handoffService;
    private final HandoffRouter handoffRouter;

    public ChatService(
            LlmService llmService,
            ChatPersonaLoader chatPersonaLoader,
            PersonaConfigLoader personaConfigLoader,
            ConversationMemoryService memoryService,
            HandoffService handoffService,
            HandoffRouter handoffRouter) {
        this.llmService = llmService;
        this.chatPersonaLoader = chatPersonaLoader;
        this.personaConfigLoader = personaConfigLoader;
        this.memoryService = memoryService;
        this.handoffService = handoffService;
        this.handoffRouter = handoffRouter;
    }

    public String chat(String userId, String personaName, String message) {
        String systemPrompt = resolveSystemPrompt(personaName);

        var history = memoryService.getContextWindow(userId, personaName);
        String historyBlock = memoryService.formatContextForPrompt(history);

        String fullPrompt = injectHistory(systemPrompt, historyBlock);

        log.debug("Chat: user={} persona={} historyTurns={}", userId, personaName, history.size());

        String reply = llmService.generateCompletion(fullPrompt, message);

        memoryService.saveTurns(userId, personaName, message, reply);

        detectAndRecordHandoff(userId, personaName, message, reply);

        return reply;
    }

    /**
     * Checks whether the AI's reply triggers an auto-handoff to another persona.
     * If the reply contains any of the source persona's {@code auto_trigger_when} phrases,
     * a PENDING handoff record is created using the persona's {@code default_target}.
     * The handoff is non-blocking — the current reply is returned to the user regardless.
     */
    private void detectAndRecordHandoff(String userId, String fromPersonaId,
                                         String userMessage, String reply) {
        try {
            if (!handoffRouter.isAutoTrigger(fromPersonaId, reply)) return;

            String defaultTarget = handoffRouter.getDefaultTarget(fromPersonaId);
            if (defaultTarget == null) return;

            var validated = handoffRouter.resolveTarget(fromPersonaId, defaultTarget);
            if (validated.isEmpty()) return;

            String sessionKey = userId + "::" + fromPersonaId;
            var signal = new HandoffSignal(
                    defaultTarget,
                    "Auto-detected: persona completed its phase",
                    reply,
                    "AUTO_DETECTED");

            handoffService.createHandoff(userId, fromPersonaId, sessionKey, signal);
            log.info("Auto-handoff created: {} → {} userId={}", fromPersonaId, defaultTarget, userId);
        } catch (Exception e) {
            log.warn("Handoff detection failed (non-critical): {}", e.getMessage());
        }
    }

    /**
     * Resolves the system prompt for a persona.
     * Tries the rich Chat Mode loader first (architect, backend-developer, etc.).
     * Falls back to the flat Review persona loader (security-engineer, sre-engineer, etc.)
     * for backward compatibility with Code Mode personas used in chat.
     */
    private String resolveSystemPrompt(String personaName) {
        if (chatPersonaLoader.hasPersona(personaName)) {
            return chatPersonaLoader.getSystemPrompt(personaName);
        }

        PersonaConfig persona = personaConfigLoader.getPersonaByName(personaName);
        if (persona != null) {
            return buildFlatSystemPrompt(persona);
        }

        throw new IllegalArgumentException("Persona not found: " + personaName);
    }

    private String buildFlatSystemPrompt(PersonaConfig persona) {
        var sb = new StringBuilder();
        sb.append("You are ").append(persona.name()).append(", a ").append(persona.role()).append(".\n");
        sb.append("Mission: ").append(persona.mission()).append("\n");
        sb.append("Focus Areas: ").append(String.join(", ", persona.focusAreas())).append("\n");
        sb.append("You are in a direct chat mode with the user. Be helpful, concise, and stick to your unique persona and expertise.");
        return sb.toString();
    }

    private String injectHistory(String systemPrompt, String historyBlock) {
        if (historyBlock.isBlank()) return systemPrompt;
        return systemPrompt
            + "\n\n" + historyBlock
            + "\nUse the conversation history above to maintain context. "
            + "Refer to prior decisions naturally without repeating them unnecessarily.";
    }
}
