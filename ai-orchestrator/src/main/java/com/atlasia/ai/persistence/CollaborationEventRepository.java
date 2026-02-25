package com.atlasia.ai.persistence;

import com.atlasia.ai.model.CollaborationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CollaborationEventRepository extends JpaRepository<CollaborationEventEntity, UUID> {
    List<CollaborationEventEntity> findByRunIdOrderByTimestampAsc(UUID runId);
    List<CollaborationEventEntity> findTop100ByRunIdOrderByTimestampDesc(UUID runId);
    
    List<CollaborationEventEntity> findByRunIdAndTimestampBetweenOrderByTimestampAsc(
        UUID runId, Instant start, Instant end);
    
    @Query("SELECT e FROM CollaborationEventEntity e WHERE e.runId = :runId AND e.eventType = :eventType ORDER BY e.timestamp ASC")
    List<CollaborationEventEntity> findByRunIdAndEventType(@Param("runId") UUID runId, @Param("eventType") String eventType);
    
    @Query("SELECT e FROM CollaborationEventEntity e WHERE e.runId = :runId AND e.userId = :userId ORDER BY e.timestamp ASC")
    List<CollaborationEventEntity> findByRunIdAndUserId(@Param("runId") UUID runId, @Param("userId") String userId);
    
    long countByRunId(UUID runId);
    
    @Query("SELECT COUNT(DISTINCT e.userId) FROM CollaborationEventEntity e WHERE e.runId = :runId")
    long countDistinctUsersByRunId(@Param("runId") UUID runId);
    
    @Query(value = "SELECT * FROM collaboration_events ORDER BY timestamp DESC LIMIT 1", nativeQuery = true)
    java.util.Optional<CollaborationEventEntity> findLatestEvent();
    
    List<CollaborationEventEntity> findByUserIdOrderByTimestampAsc(String userId);
}
