package com.atlasia.ai.service;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private LlmService llmService;
    @Mock private PersonaConfigLoader personaConfigLoader;
    @Mock private ConversationMemoryService memoryService;

    @InjectMocks private ChatService chatService;

    private PersonaConfig samplePersona;

    @BeforeEach
    void setUp() {
        samplePersona = new PersonaConfig(
                "security-engineer",
                "Security Engineer",
                "Identify vulnerabilities",
                List.of("OWASP", "threat modeling"),
                List.of(),
                java.util.Map.of(),
                List.of()
        );
    }

    @Test
    void chat_freshSession_returnsReplyAndSavesTurns() {
        when(personaConfigLoader.getPersonaByName("security-engineer")).thenReturn(samplePersona);
        when(memoryService.getContextWindow("user1", "security-engineer")).thenReturn(List.of());
        when(memoryService.formatContextForPrompt(List.of())).thenReturn("");
        when(llmService.generateCompletion(anyString(), eq("Is my JWT safe?"))).thenReturn("Yes, looks secure.");

        var reply = chatService.chat("user1", "security-engineer", "Is my JWT safe?");

        assertEquals("Yes, looks secure.", reply);
        verify(memoryService).saveTurns("user1", "security-engineer", "Is my JWT safe?", "Yes, looks secure.");
    }

    @Test
    void chat_withHistory_injectsHistoryIntoPrompt() {
        var turn = mock(ConversationTurnEntity.class);

        when(personaConfigLoader.getPersonaByName("security-engineer")).thenReturn(samplePersona);
        when(memoryService.getContextWindow("user1", "security-engineer")).thenReturn(List.of(turn));
        when(memoryService.formatContextForPrompt(List.of(turn))).thenReturn("## Conversation history\n[user]: previous question\n## End of history\n");
        when(llmService.generateCompletion(anyString(), anyString())).thenReturn("Follow-up reply.");

        chatService.chat("user1", "security-engineer", "follow-up");

        verify(llmService).generateCompletion(contains("Conversation history"), eq("follow-up"));
    }

    @Test
    void chat_unknownPersona_throwsIllegalArgument() {
        when(personaConfigLoader.getPersonaByName("unknown")).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> chatService.chat("user1", "unknown", "hello"));

        verifyNoInteractions(llmService, memoryService);
    }
}
