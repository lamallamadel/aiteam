package com.atlasia.ai.controller;

import com.atlasia.ai.model.PersonaHandoffEntity;
import com.atlasia.ai.service.ChatService;
import com.atlasia.ai.service.ConversationMemoryService;
import com.atlasia.ai.service.HandoffService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    private final ChatService chatService;
    private final ConversationMemoryService memoryService;
    private final HandoffService handoffService;

    public ChatController(ChatService chatService,
                          ConversationMemoryService memoryService,
                          HandoffService handoffService) {
        this.chatService = chatService;
        this.memoryService = memoryService;
        this.handoffService = handoffService;
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

    /**
     * GET /api/chat/handoffs?userId=...
     * Returns all PENDING handoffs for a user so the Angular UI can prompt
     * the user to continue with the suggested next persona.
     */
    @GetMapping("/handoffs")
    public ResponseEntity<List<PersonaHandoffEntity>> getPendingHandoffs(
            @RequestParam @NotBlank String userId) {
        return ResponseEntity.ok(handoffService.getPendingHandoffsForUser(userId));
    }

    /**
     * POST /api/chat/handoffs/{handoffId}/accept
     * Body: { "toSessionKey": "userId::toPersonaId" }
     * Accepts the handoff — the receiving persona's next chat turn will include
     * the full briefing from the source persona.
     */
    @PostMapping("/handoffs/{handoffId}/accept")
    public ResponseEntity<PersonaHandoffEntity> acceptHandoff(
            @PathVariable UUID handoffId,
            @RequestBody Map<String, String> body) {
        String toSessionKey = body.getOrDefault("toSessionKey", "");
        if (toSessionKey.isBlank()) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(handoffService.acceptHandoff(handoffId, toSessionKey));
    }

    /**
     * GET /api/chat/handoffs/{handoffId}/briefing
     * Returns the markdown briefing block the receiving persona will receive.
     * Used by the Angular UI to show a preview before accepting.
     */
    @GetMapping("/handoffs/{handoffId}/briefing")
    public ResponseEntity<Map<String, String>> getHandoffBriefing(@PathVariable UUID handoffId) {
        return handoffService.findById(handoffId)
                .map(h -> ResponseEntity.ok(Map.of(
                        "briefing", handoffService.buildReceivingPersonaBriefing(handoffId),
                        "fromPersona", h.getFromPersonaId(),
                        "toPersona", h.getToPersonaId(),
                        "status", h.getStatus())))
                .orElse(ResponseEntity.notFound().build());
    }
}
