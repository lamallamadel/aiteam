package com.atlasia.ai.persistence;

import com.atlasia.ai.model.CollaborationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CollaborationEventRepository extends JpaRepository<CollaborationEventEntity, UUID> {
    List<CollaborationEventEntity> findByRunIdOrderByTimestampAsc(UUID runId);
    List<CollaborationEventEntity> findTop100ByRunIdOrderByTimestampDesc(UUID runId);
}
