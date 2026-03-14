package com.atlasia.ai.persistence;

import com.atlasia.ai.model.ConversationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSessionEntity, UUID> {

    Optional<ConversationSessionEntity> findBySessionKey(String sessionKey);
}
