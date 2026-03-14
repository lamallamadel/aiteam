package com.atlasia.ai.controller;

import com.atlasia.ai.service.ChatService;
import com.atlasia.ai.service.ConversationMemoryService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    private final ChatService chatService;
    private final ConversationMemoryService memoryService;

    public ChatController(ChatService chatService, ConversationMemoryService memoryService) {
        this.chatService = chatService;
        this.memoryService = memoryService;
    }

    /**
     * POST /api/chat/{personaName}
     * Body: { "userId": "...", "message": "..." }
     */
    @PostMapping("/{personaName}")
    public ResponseEntity<Map<String, String>> chat(
            @PathVariable("personaName") String personaName,
            @RequestBody Map<String, String> request) {

        String userId = request.get("userId");
        String message = request.get("message");

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String response = chatService.chat(userId, personaName, message);
        return ResponseEntity.ok(Map.of("response", response));
    }

    /**
     * DELETE /api/chat/{personaName}/session?userId=...
     * Clears conversation memory — "New conversation" button in the Angular UI.
     */
    @DeleteMapping("/{personaName}/session")
    public ResponseEntity<Void> clearSession(
            @PathVariable("personaName") String personaName,
            @RequestParam @NotBlank String userId) {
        memoryService.clearSession(userId, personaName);
        return ResponseEntity.noContent().build();
    }
}
