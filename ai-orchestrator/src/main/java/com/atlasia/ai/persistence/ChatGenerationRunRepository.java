package com.atlasia.ai.persistence;

import com.atlasia.ai.model.ChatGenerationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatGenerationRunRepository extends JpaRepository<ChatGenerationRunEntity, UUID> {

    List<ChatGenerationRunEntity> findBySessionKeyOrderByCreatedAtDesc(String sessionKey);
}
