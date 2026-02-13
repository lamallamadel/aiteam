package com.atlasia.ai.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class WorkflowEventBus {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEventBus.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public WorkflowEventBus() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public SseEmitter registerEmitter(UUID runId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> removeEmitter(runId, emitter));
        emitter.onError(e -> {
            log.debug("SSE emitter error for runId={}: {}", runId, e.getMessage());
            removeEmitter(runId, emitter);
        });

        log.info("SSE emitter registered: runId={}, activeEmitters={}", runId, getEmitterCount(runId));
        return emitter;
    }

    public void emit(UUID runId, WorkflowEvent event) {
        CopyOnWriteArrayList<SseEmitter> runEmitters = emitters.get(runId);
        if (runEmitters == null || runEmitters.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event()
                    .name(event.eventType())
                    .data(json);

            for (SseEmitter emitter : runEmitters) {
                try {
                    emitter.send(eventBuilder);
                } catch (IOException e) {
                    log.debug("Failed to send SSE event to emitter for runId={}: {}", runId, e.getMessage());
                    removeEmitter(runId, emitter);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to serialize SSE event for runId={}: {}", runId, e.getMessage());
        }
    }

    public void completeEmitters(UUID runId) {
        CopyOnWriteArrayList<SseEmitter> runEmitters = emitters.remove(runId);
        if (runEmitters != null) {
            for (SseEmitter emitter : runEmitters) {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("Error completing SSE emitter for runId={}: {}", runId, e.getMessage());
                }
            }
            log.info("SSE emitters completed for runId={}", runId);
        }
    }

    private void removeEmitter(UUID runId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> runEmitters = emitters.get(runId);
        if (runEmitters != null) {
            runEmitters.remove(emitter);
            if (runEmitters.isEmpty()) {
                emitters.remove(runId);
            }
        }
    }

    private int getEmitterCount(UUID runId) {
        CopyOnWriteArrayList<SseEmitter> runEmitters = emitters.get(runId);
        return runEmitters != null ? runEmitters.size() : 0;
    }
}
