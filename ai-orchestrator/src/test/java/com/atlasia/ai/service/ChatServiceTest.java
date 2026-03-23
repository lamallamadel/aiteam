package com.atlasia.ai.service;

import com.atlasia.ai.api.AiProviderRouter;
import com.atlasia.ai.api.dto.AiWireTypes.AiPrompt;
import com.atlasia.ai.api.dto.AiWireTypes.AiResponse;
import com.atlasia.ai.config.ChatPersonaLoader;
import com.atlasia.ai.config.PersonaConfig;
import com.atlasia.ai.config.PersonaConfigLoader;
import com.atlasia.ai.model.ConversationTurnEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private AiProviderRouter aiProviderRouter;

    @Mock
    private ChatPersonaLoader chatPersonaLoader;

    @Mock
    private PersonaConfigLoader personaConfigLoader;

    @Mock
    private ConversationMemoryService memoryService;

    @Mock
    private HandoffService handoffService;

    @Mock
    private HandoffRouter handoffRouter;

    @InjectMocks
    private ChatService chatService;

    private PersonaConfig reviewPersona;

    @BeforeEach
    void setUp() {
        lenient().when(handoffRouter.isAutoTrigger(anyString(), anyString())).thenReturn(false);
        reviewPersona = new PersonaConfig(
                "security-engineer",
                "Security Engineer",
                "Identify vulnerabilities",
                List.of("OWASP", "threat modeling"),
                List.of(),
                Map.of(),
                List.of());
    }

    @Test
    void chat_richPersona_usesRichSystemPrompt() {
        when(chatPersonaLoader.hasPersona("architect")).thenReturn(true);
        when(chatPersonaLoader.getSystemPrompt("architect")).thenReturn("# Identity\nYou are an architect.");
        when(memoryService.getContextWindow("user1", "architect")).thenReturn(List.of());
        when(aiProviderRouter.call(eq("architect"), any(AiPrompt.class)))
                .thenReturn(new AiResponse("Design advice.", 0, 0, "test", "test", 0L));

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
        when(aiProviderRouter.call(eq("security-engineer"), any(AiPrompt.class)))
                .thenReturn(new AiResponse("Looks secure.", 0, 0, "test", "test", 0L));

        var reply = chatService.chat("user1", "security-engineer", "Is my JWT safe?");

        assertEquals("Looks secure.", reply);
        verify(personaConfigLoader).getPersonaByName("security-engineer");
    }

    @Test
    void chat_withHistory_passesHistoryToRouter() {
        var turn = mock(ConversationTurnEntity.class);
        when(turn.getRole()).thenReturn("user");
        when(turn.getContent()).thenReturn("previous");
        when(chatPersonaLoader.hasPersona("architect")).thenReturn(true);
        when(chatPersonaLoader.getSystemPrompt("architect")).thenReturn("# Identity\nYou are an architect.");
        when(memoryService.getContextWindow("user1", "architect")).thenReturn(List.of(turn));
        when(aiProviderRouter.call(eq("architect"), any(AiPrompt.class)))
                .thenReturn(new AiResponse("Follow-up.", 0, 0, "test", "test", 0L));

        chatService.chat("user1", "architect", "follow-up");

        ArgumentCaptor<AiPrompt> promptCaptor = ArgumentCaptor.forClass(AiPrompt.class);
        verify(aiProviderRouter).call(eq("architect"), promptCaptor.capture());
        AiPrompt sent = promptCaptor.getValue();
        assertEquals("follow-up", sent.userMessage());
        assertEquals(1, sent.history().size());
        assertEquals("user", sent.history().get(0).get("role"));
        assertEquals("previous", sent.history().get(0).get("content"));
    }

    @Test
    void chat_unknownPersona_throwsIllegalArgument() {
        when(chatPersonaLoader.hasPersona("unknown")).thenReturn(false);
        when(personaConfigLoader.getPersonaByName("unknown")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> chatService.chat("user1", "unknown", "hello"));

        verifyNoInteractions(aiProviderRouter, memoryService);
    }
}
