package com.atlasia.ai.persistence;

import com.atlasia.ai.model.TraceEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TraceEventRepository extends JpaRepository<TraceEventEntity, UUID> {

    List<TraceEventEntity> findByRunIdOrderByStartTimeAsc(UUID runId);

    List<TraceEventEntity> findByRunIdAndParentEventIdIsNullOrderByStartTimeAsc(UUID runId);

    List<TraceEventEntity> findByParentEventIdOrderByStartTimeAsc(UUID parentEventId);
}
