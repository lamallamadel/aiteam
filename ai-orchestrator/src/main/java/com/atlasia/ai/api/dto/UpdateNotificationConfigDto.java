package com.atlasia.ai.api.dto;

import java.util.List;

public record UpdateNotificationConfigDto(
        String webhookUrl,
        List<String> enabledEvents,
        Boolean enabled
) {}
