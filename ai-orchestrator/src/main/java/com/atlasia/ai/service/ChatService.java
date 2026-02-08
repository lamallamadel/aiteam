package com.atlasia.ai.service;

import com.atlasia.ai.config.PersonaConfig;
import com.atlasia.ai.config.PersonaConfigLoader;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class ChatService {

    private final LlmService llmService;
    private final PersonaConfigLoader personaConfigLoader;

    public ChatService(LlmService llmService, PersonaConfigLoader personaConfigLoader) {
        this.llmService = llmService;
        this.personaConfigLoader = personaConfigLoader;
    }

    public String chat(String personaName, String message) {
        PersonaConfig persona = personaConfigLoader.getPersonaByName(personaName);
        if (persona == null) {
            throw new IllegalArgumentException("Persona not found: " + personaName);
        }

        String systemPrompt = buildSystemPrompt(persona);
        return llmService.generateCompletion(systemPrompt, message);
    }

    private String buildSystemPrompt(PersonaConfig persona) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(persona.name()).append(", a ").append(persona.role()).append(".\n");
        sb.append("Mission: ").append(persona.mission()).append("\n");
        sb.append("Focus Areas: ").append(String.join(", ", persona.focusAreas())).append("\n");
        sb.append(
                "You are in a direct chat mode with the user. Be helpful, concise, and stick to your unique persona and expertise.");
        return sb.toString();
    }
}
