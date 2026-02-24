package com.atlasia.ai.api.dto;

import java.util.Set;
import java.util.UUID;

public record CurrentUserDto(
    UUID id,
    String username,
    String email,
    Set<String> roles,
    Set<String> permissions,
    boolean enabled,
    boolean locked
) {}
