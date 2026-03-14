package com.atlasia.ai.service;

import com.atlasia.ai.config.ChatPersonaLoader;
import com.atlasia.ai.config.PersonaConfig;
import com.atlasia.ai.config.PersonaConfigLoader;
import com.atlasia.ai.model.ConversationTurnEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private LlmService llmService;
    @Mock private ChatPersonaLoader chatPersonaLoader;
    @Mock private PersonaConfigLoader personaConfigLoader;
    @Mock private ConversationMemoryService memoryService;

    @InjectMocks private ChatService chatService;

    private PersonaConfig reviewPersona;

    @BeforeEach
    void setUp() {
        reviewPersona = new PersonaConfig(
                "security-engineer",
                "Security Engineer",
                "Identify vulnerabilities",
                List.of("OWASP", "threat modeling"),
                List.of(),
                Map.of(),
                List.of()
        );
    }

    @Test
    void chat_richPersona_usesRichSystemPrompt() {
        when(chatPersonaLoader.hasPersona("architect")).thenReturn(true);
        when(chatPersonaLoader.getSystemPrompt("architect")).thenReturn("# Identity\nYou are an architect.");
        when(memoryService.getContextWindow("user1", "architect")).thenReturn(List.of());
        when(memoryService.formatContextForPrompt(List.of())).thenReturn("");
        when(llmService.generateCompletion(anyString(), anyString())).thenReturn("Design advice.");

        var reply = chatService.chat("user1", "architect", "Design the auth module.");

        assertEquals("Design advice.", reply);
        verify(chatPersonaLoader).getSystemPrompt("architect");
        verifyNoInteractions(personaConfigLoader);
        verify(memoryService).saveTurns("user1", "architect", "Design the auth module.", "Design advice.");
    }

    @Test
    void chat_reviewPersona_fallsBackToFlatLoader() {
        when(chatPersonaLoader.hasPersona("security-engineer")).thenReturn(false);
        when(personaConfigLoader.getPersonaByName("security-engineer")).thenReturn(reviewPersona);
        when(memoryService.getContextWindow("user1", "security-engineer")).thenReturn(List.of());
        when(memoryService.formatContextForPrompt(List.of())).thenReturn("");
        when(llmService.generateCompletion(anyString(), anyString())).thenReturn("Looks secure.");

        var reply = chatService.chat("user1", "security-engineer", "Is my JWT safe?");

        assertEquals("Looks secure.", reply);
        verify(personaConfigLoader).getPersonaByName("security-engineer");
    }

    @Test
    void chat_withHistory_injectsHistoryIntoPrompt() {
        var turn = mock(ConversationTurnEntity.class);
        when(chatPersonaLoader.hasPersona("architect")).thenReturn(true);
        when(chatPersonaLoader.getSystemPrompt("architect")).thenReturn("# Identity\nYou are an architect.");
        when(memoryService.getContextWindow("user1", "architect")).thenReturn(List.of(turn));
        when(memoryService.formatContextForPrompt(List.of(turn))).thenReturn("## Conversation history\n[user]: previous\n## End of history\n");
        when(llmService.generateCompletion(anyString(), anyString())).thenReturn("Follow-up.");

        chatService.chat("user1", "architect", "follow-up");

        verify(llmService).generateCompletion(contains("Conversation history"), eq("follow-up"));
    }

    @Test
    void chat_unknownPersona_throwsIllegalArgument() {
        when(chatPersonaLoader.hasPersona("unknown")).thenReturn(false);
        when(personaConfigLoader.getPersonaByName("unknown")).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> chatService.chat("user1", "unknown", "hello"));

        verifyNoInteractions(llmService, memoryService);
    }
}
