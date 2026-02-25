package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.CreateNotificationConfigDto;
import com.atlasia.ai.api.dto.NotificationConfigDto;
import com.atlasia.ai.api.dto.UpdateNotificationConfigDto;
import com.atlasia.ai.service.CurrentUserService;
import com.atlasia.ai.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationService notificationService,
                                  CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/configs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationConfigDto>> getNotificationConfigs() {
        UUID userId = currentUserService.getCurrentUserId();
        List<NotificationConfigDto> configs = notificationService.getNotificationConfigs(userId);
        return ResponseEntity.ok(configs);
    }

    @PostMapping("/configs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationConfigDto> createNotificationConfig(
            @Valid @RequestBody CreateNotificationConfigDto dto) {
        UUID userId = currentUserService.getCurrentUserId();
        NotificationConfigDto config = notificationService.createNotificationConfig(userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(config);
    }

    @PutMapping("/configs/{configId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationConfigDto> updateNotificationConfig(
            @PathVariable UUID configId,
            @Valid @RequestBody UpdateNotificationConfigDto dto) {
        UUID userId = currentUserService.getCurrentUserId();
        NotificationConfigDto config = notificationService.updateNotificationConfig(configId, userId, dto);
        return ResponseEntity.ok(config);
    }

    @DeleteMapping("/configs/{configId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteNotificationConfig(@PathVariable UUID configId) {
        UUID userId = currentUserService.getCurrentUserId();
        notificationService.deleteNotificationConfig(configId, userId);
        return ResponseEntity.noContent().build();
    }
}
