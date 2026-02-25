package com.atlasia.ai.service;

import com.atlasia.ai.model.CrdtSnapshotEntity;
import com.atlasia.ai.persistence.CrdtSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CrdtSnapshotService {
    
    private static final Logger logger = LoggerFactory.getLogger(CrdtSnapshotService.class);
    private static final int SNAPSHOT_INTERVAL_EVENTS = 100;
    
    private final CrdtDocumentManager documentManager;
    private final CrdtSnapshotRepository snapshotRepository;
    private final CrdtSyncService syncService;
    private final Map<UUID, Integer> eventCounters = new ConcurrentHashMap<>();
    
    public CrdtSnapshotService(CrdtDocumentManager documentManager,
                              CrdtSnapshotRepository snapshotRepository,
                              CrdtSyncService syncService) {
        this.documentManager = documentManager;
        this.snapshotRepository = snapshotRepository;
        this.syncService = syncService;
    }
    
    public void incrementEventCount(UUID runId) {
        int count = eventCounters.merge(runId, 1, Integer::sum);
        
        if (count >= SNAPSHOT_INTERVAL_EVENTS) {
            createSnapshot(runId);
            eventCounters.put(runId, 0);
        }
    }
    
    @Transactional
    public void createSnapshot(UUID runId) {
        try {
            byte[] snapshotData = documentManager.getChanges(runId);
            if (snapshotData.length == 0) {
                return;
            }
            
            Integer eventCount = eventCounters.getOrDefault(runId, 0);
            
            CrdtSnapshotEntity snapshot = new CrdtSnapshotEntity(
                runId,
                snapshotData,
                System.currentTimeMillis(),
                syncService.getLocalRegion(),
                Instant.now(),
                eventCount
            );
            
            snapshotRepository.save(snapshot);
            logger.info("Created CRDT snapshot for run {} with {} events", runId, eventCount);
            
            cleanupOldSnapshots(runId);
        } catch (Exception e) {
            logger.error("Failed to create CRDT snapshot for run {}: {}", runId, e.getMessage());
        }
    }
    
    @Transactional
    public void restoreFromSnapshot(UUID runId) {
        Optional<CrdtSnapshotEntity> latestSnapshot = snapshotRepository.findLatestByRunId(runId);
        
        if (latestSnapshot.isPresent()) {
            CrdtSnapshotEntity snapshot = latestSnapshot.get();
            documentManager.applyChanges(runId, snapshot.getSnapshotData());
            logger.info("Restored CRDT state for run {} from snapshot at {}", 
                       runId, snapshot.getCreatedAt());
        } else {
            logger.info("No snapshot found for run {}, starting with empty state", runId);
        }
    }
    
    private void cleanupOldSnapshots(UUID runId) {
        List<CrdtSnapshotEntity> snapshots = snapshotRepository.findByRunIdOrderByCreatedAtDesc(runId);
        
        if (snapshots.size() > 10) {
            List<CrdtSnapshotEntity> toDelete = snapshots.subList(10, snapshots.size());
            snapshotRepository.deleteAll(toDelete);
            logger.debug("Cleaned up {} old snapshots for run {}", toDelete.size(), runId);
        }
    }
    
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void periodicSnapshotAll() {
        logger.debug("Running periodic snapshot for all active runs");
        
        Set<UUID> activeRuns = new HashSet<>(eventCounters.keySet());
        for (UUID runId : activeRuns) {
            if (eventCounters.getOrDefault(runId, 0) > 0) {
                createSnapshot(runId);
                eventCounters.put(runId, 0);
            }
        }
    }
    
    public void cleanupRun(UUID runId) {
        eventCounters.remove(runId);
    }
}
