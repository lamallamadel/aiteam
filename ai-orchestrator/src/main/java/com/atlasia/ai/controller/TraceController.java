package com.atlasia.ai.controller;

import com.atlasia.ai.model.TraceEventEntity;
import com.atlasia.ai.persistence.TraceEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/traces")
@Validated
public class TraceController {

    private final TraceEventRepository traceEventRepository;

    public TraceController(TraceEventRepository traceEventRepository) {
        this.traceEventRepository = traceEventRepository;
    }

    /**
     * GET /api/traces/{runId} — flat list of all trace events for a run
     */
    @GetMapping("/{runId}")
    public ResponseEntity<List<TraceSpanDto>> getTraceEvents(@PathVariable("runId") UUID runId) {
        List<TraceEventEntity> events = traceEventRepository.findByRunIdOrderByStartTimeAsc(runId);
        List<TraceSpanDto> dtos = events.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/traces/{runId}/waterfall — nested hierarchy of trace events
     */
    @GetMapping("/{runId}/waterfall")
    public ResponseEntity<List<WaterfallSpanDto>> getWaterfallTrace(@PathVariable("runId") UUID runId) {
        List<TraceEventEntity> allEvents = traceEventRepository.findByRunIdOrderByStartTimeAsc(runId);

        // Build lookup map
        Map<UUID, TraceEventEntity> entityMap = new LinkedHashMap<>();
        for (TraceEventEntity e : allEvents) {
            entityMap.put(e.getId(), e);
        }

        // Build children map
        Map<UUID, List<TraceEventEntity>> childrenMap = new LinkedHashMap<>();
        List<TraceEventEntity> roots = new ArrayList<>();

        for (TraceEventEntity e : allEvents) {
            if (e.getParentEventId() == null) {
                roots.add(e);
            } else {
                childrenMap.computeIfAbsent(e.getParentEventId(), k -> new ArrayList<>()).add(e);
            }
        }

        List<WaterfallSpanDto> waterfall = roots.stream()
                .map(root -> buildWaterfallNode(root, childrenMap))
                .collect(Collectors.toList());

        return ResponseEntity.ok(waterfall);
    }

    private WaterfallSpanDto buildWaterfallNode(TraceEventEntity entity,
                                                 Map<UUID, List<TraceEventEntity>> childrenMap) {
        List<WaterfallSpanDto> children = childrenMap.getOrDefault(entity.getId(), List.of())
                .stream()
                .map(child -> buildWaterfallNode(child, childrenMap))
                .collect(Collectors.toList());

        return new WaterfallSpanDto(
                entity.getId(),
                entity.getParentEventId(),
                entity.getEventType(),
                entity.getAgentName(),
                entity.getLabel(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getDurationMs(),
                entity.getTokensUsed(),
                entity.getMetadata(),
                children);
    }

    private TraceSpanDto toDto(TraceEventEntity entity) {
        return new TraceSpanDto(
                entity.getId(),
                entity.getRunId(),
                entity.getParentEventId(),
                entity.getEventType(),
                entity.getAgentName(),
                entity.getLabel(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getDurationMs(),
                entity.getTokensUsed(),
                entity.getMetadata());
    }

    public record TraceSpanDto(
            UUID id,
            UUID runId,
            UUID parentEventId,
            String eventType,
            String agentName,
            String label,
            java.time.Instant startTime,
            java.time.Instant endTime,
            Long durationMs,
            Integer tokensUsed,
            String metadata) {}

    public record WaterfallSpanDto(
            UUID id,
            UUID parentEventId,
            String eventType,
            String agentName,
            String label,
            java.time.Instant startTime,
            java.time.Instant endTime,
            Long durationMs,
            Integer tokensUsed,
            String metadata,
            List<WaterfallSpanDto> children) {}
}
