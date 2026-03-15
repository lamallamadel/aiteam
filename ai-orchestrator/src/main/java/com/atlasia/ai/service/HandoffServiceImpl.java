package com.atlasia.ai.service;

import com.atlasia.ai.model.PersonaHandoffEntity;
import com.atlasia.ai.persistence.PersonaHandoffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HandoffServiceImpl implements HandoffService {

    private static final Logger log = LoggerFactory.getLogger(HandoffServiceImpl.class);

    private final PersonaHandoffRepository handoffRepository;

    public HandoffServiceImpl(PersonaHandoffRepository handoffRepository) {
        this.handoffRepository = handoffRepository;
    }

    @Override
    @Transactional
    public PersonaHandoffEntity createHandoff(
            String userId, String fromPersonaId, String fromSessionKey, HandoffSignal signal) {

        var entity = new PersonaHandoffEntity(
                userId,
                fromPersonaId,
                fromSessionKey,
                signal.toPersonaId(),
                signal.triggerType(),
                signal.triggerReason(),
                signal.handoffSummary());

        handoffRepository.save(entity);
        log.info("Handoff created: {} → {} id={} userId={}", fromPersonaId, signal.toPersonaId(),
                entity.getId(), userId);
        return entity;
    }

    @Override
    @Transactional
    public PersonaHandoffEntity acceptHandoff(UUID handoffId, String toSessionKey) {
        var handoff = handoffRepository.findById(handoffId)
                .orElseThrow(() -> new IllegalArgumentException("Handoff not found: " + handoffId));

        handoff.setStatus("ACCEPTED");
        handoff.setToSessionKey(toSessionKey);
        handoff.setAcceptedAt(Instant.now());

        log.info("Handoff accepted: → {} id={}", handoff.getToPersonaId(), handoffId);
        return handoffRepository.save(handoff);
    }

    /**
     * Builds the briefing block injected into the receiving persona's system prompt.
     * This gives the receiving persona full context without the user re-explaining anything.
     */
    @Override
    @Transactional(readOnly = true)
    public String buildReceivingPersonaBriefing(UUID handoffId) {
        var handoff = handoffRepository.findById(handoffId)
                .orElseThrow(() -> new IllegalArgumentException("Handoff not found: " + handoffId));

        var sb = new StringBuilder();
        sb.append("## Handoff briefing from ").append(formatPersonaName(handoff.getFromPersonaId())).append("\n");
        sb.append(handoff.getTriggerReason()).append("\n\n");
        sb.append("### What was completed\n").append(handoff.getHandoffSummary()).append("\n\n");

        if (handoff.getSharedContext() != null && !handoff.getSharedContext().isBlank()) {
            sb.append("### Shared context\n").append(handoff.getSharedContext()).append("\n\n");
        }

        sb.append("Proceed based on the above context. Do not ask the user to repeat what has already been decided.\n");
        return sb.toString();
    }

    @Override
    @Transactional
    public void completeHandoff(UUID handoffId) {
        handoffRepository.findById(handoffId).ifPresent(h -> {
            h.setStatus("COMPLETED");
            h.setCompletedAt(Instant.now());
            handoffRepository.save(h);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonaHandoffEntity> getPendingHandoffsForUser(String userId) {
        return handoffRepository.findByUserIdAndStatus(userId, "PENDING");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonaHandoffEntity> findById(UUID handoffId) {
        return handoffRepository.findById(handoffId);
    }

    private String formatPersonaName(String personaId) {
        return Arrays.stream(personaId.split("-"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }
}
