package com.atlasia.ai.persistence;

import com.atlasia.ai.model.PersistedCollaborationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PersistedCollaborationMessageRepository extends JpaRepository<PersistedCollaborationMessage, UUID> {
    
    List<PersistedCollaborationMessage> findByRunIdOrderBySequenceNumberDesc(UUID runId);
    
    @Query("SELECT m FROM PersistedCollaborationMessage m WHERE m.runId = :runId AND m.sequenceNumber > :afterSequence ORDER BY m.sequenceNumber ASC")
    List<PersistedCollaborationMessage> findByRunIdAfterSequence(UUID runId, Long afterSequence);
    
    @Query("SELECT m FROM PersistedCollaborationMessage m WHERE m.runId = :runId AND m.isCritical = true ORDER BY m.sequenceNumber DESC")
    List<PersistedCollaborationMessage> findCriticalMessagesByRunId(UUID runId);
    
    @Query("SELECT MAX(m.sequenceNumber) FROM PersistedCollaborationMessage m WHERE m.runId = :runId")
    Long findMaxSequenceNumberByRunId(UUID runId);
    
    @Modifying
    @Query("DELETE FROM PersistedCollaborationMessage m WHERE m.runId = :runId AND m.sequenceNumber NOT IN (SELECT m2.sequenceNumber FROM PersistedCollaborationMessage m2 WHERE m2.runId = :runId ORDER BY m2.sequenceNumber DESC LIMIT :keepCount)")
    void deleteOldMessagesKeepingLatest(UUID runId, int keepCount);
    
    @Modifying
    @Query("DELETE FROM PersistedCollaborationMessage m WHERE m.timestamp < :cutoffTime")
    void deleteMessagesOlderThan(Instant cutoffTime);
}
