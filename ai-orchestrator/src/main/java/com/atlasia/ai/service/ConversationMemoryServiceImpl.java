package com.atlasia.ai.service;

import com.atlasia.ai.model.ConversationSessionEntity;
import com.atlasia.ai.model.ConversationTurnEntity;
import com.atlasia.ai.persistence.ConversationSessionRepository;
import com.atlasia.ai.persistence.ConversationTurnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ConversationMemoryServiceImpl implements ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryServiceImpl.class);

    private final ConversationSessionRepository sessionRepository;
    private final ConversationTurnRepository turnRepository;

    @Value("${atlasia.orchestrator.chat.memory-window-size:10}")
    private int windowSize;

    public ConversationMemoryServiceImpl(
            ConversationSessionRepository sessionRepository,
            ConversationTurnRepository turnRepository) {
        this.sessionRepository = sessionRepository;
        this.turnRepository = turnRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationTurnEntity> getContextWindow(String userId, String personaId) {
        return sessionRepository.findBySessionKey(buildSessionKey(userId, personaId))
                .map(session -> turnRepository.findLastNBySessionId(
                        session.getId(),
                        PageRequest.of(0, windowSize)))
                .orElse(List.of());
    }

    @Override
    @Transactional
    public void saveTurns(String userId, String personaId, String userMessage, String assistantReply) {
        var session = getOrCreateSession(userId, personaId);

        turnRepository.save(new ConversationTurnEntity(session, "user", userMessage));
        turnRepository.save(new ConversationTurnEntity(session, "assistant", assistantReply));

        session.setLastActive(Instant.now());
        sessionRepository.save(session);

        log.debug("Saved 2 turns for session {} (user={} persona={})", session.getId(), userId, personaId);
    }

    @Override
    @Transactional
    public void clearSession(String userId, String personaId) {
        sessionRepository.findBySessionKey(buildSessionKey(userId, personaId))
                .ifPresent(session -> {
                    sessionRepository.delete(session);
                    log.info("Cleared conversation session for user={} persona={}", userId, personaId);
                });
    }

    @Override
    public String formatContextForPrompt(List<ConversationTurnEntity> turns) {
        if (turns.isEmpty()) return "";
        var sb = new StringBuilder("## Conversation history\n");
        for (var turn : turns) {
            sb.append("[").append(turn.getRole()).append("]: ")
              .append(turn.getContent()).append("\n");
        }
        sb.append("## End of history\n");
        return sb.toString();
    }

    private ConversationSessionEntity getOrCreateSession(String userId, String personaId) {
        String key = buildSessionKey(userId, personaId);
        return sessionRepository.findBySessionKey(key).orElseGet(() -> {
            log.info("Creating new conversation session for user={} persona={}", userId, personaId);
            return sessionRepository.save(new ConversationSessionEntity(key, userId, personaId));
        });
    }

    private String buildSessionKey(String userId, String personaId) {
        return userId + "::" + personaId;
    }
}
