package com.atlasia.ai.controller;

import com.atlasia.ai.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/{personaName}")
    public ResponseEntity<Map<String, String>> chat(
            @PathVariable String personaName,
            @RequestBody Map<String, String> request) {

        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String response = chatService.chat(personaName, message);
        return ResponseEntity.ok(Map.of("response", response));
    }
}
