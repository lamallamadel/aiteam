package com.atlasia.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CreateNotificationConfigDto(
        @NotBlank(message = "Provider is required")
        @Pattern(regexp = "slack|discord", message = "Provider must be 'slack' or 'discord'")
        String provider,

        @NotBlank(message = "Webhook URL is required")
        String webhookUrl,

        @NotNull(message = "Enabled events are required")
        List<String> enabledEvents
) {}
