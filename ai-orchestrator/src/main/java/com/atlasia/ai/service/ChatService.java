package com.atlasia.ai.service;

import com.atlasia.ai.api.AiProviderRouter;
import com.atlasia.ai.api.dto.AiWireTypes.AiPrompt;
import com.atlasia.ai.config.ChatPersonaLoader;
import com.atlasia.ai.config.PersonaConfig;
import com.atlasia.ai.config.PersonaConfigLoader;
import com.atlasia.ai.model.ConversationTurnEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final AiProviderRouter aiProviderRouter;
    private final ChatPersonaLoader chatPersonaLoader;
    private final PersonaConfigLoader personaConfigLoader;
    private final ConversationMemoryService memoryService;
    private final HandoffService handoffService;
    private final HandoffRouter handoffRouter;

    public ChatService(
            AiProviderRouter aiProviderRouter,
            ChatPersonaLoader chatPersonaLoader,
            PersonaConfigLoader personaConfigLoader,
            ConversationMemoryService memoryService,
            HandoffService handoffService,
            HandoffRouter handoffRouter) {
        this.aiProviderRouter = aiProviderRouter;
        this.chatPersonaLoader = chatPersonaLoader;
        this.personaConfigLoader = personaConfigLoader;
        this.memoryService = memoryService;
        this.handoffService = handoffService;
        this.handoffRouter = handoffRouter;
    }

    public String chat(String userId, String personaName, String message) {
        String systemPrompt = resolveSystemPrompt(personaName);

        List<ConversationTurnEntity> history = memoryService.getContextWindow(userId, personaName);
        List<Map<String, String>> histMaps = new ArrayList<>();
        for (ConversationTurnEntity t : history) {
            histMaps.add(Map.of("role", t.getRole(), "content", t.getContent()));
        }

        log.debug("Chat: user={} persona={} historyTurns={}", userId, personaName, history.size());

        var response = aiProviderRouter.call(personaName, new AiPrompt(systemPrompt, message, histMaps));
        String reply = response.content();

        memoryService.saveTurns(userId, personaName, message, reply);

        detectAndRecordHandoff(userId, personaName, message, reply);

        return reply;
    }

    private void detectAndRecordHandoff(
            String userId, String fromPersonaId, String userMessage, String reply) {
        try {
            if (!handoffRouter.isAutoTrigger(fromPersonaId, reply)) {
                return;
            }

            String defaultTarget = handoffRouter.getDefaultTarget(fromPersonaId);
            if (defaultTarget == null) {
                return;
            }

            var validated = handoffRouter.resolveTarget(fromPersonaId, defaultTarget);
            if (validated.isEmpty()) {
                return;
            }

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
        sb.append(
                "You are in a direct chat mode with the user. Be helpful, concise, and stick to your unique persona and expertise.");
        return sb.toString();
    }
}
