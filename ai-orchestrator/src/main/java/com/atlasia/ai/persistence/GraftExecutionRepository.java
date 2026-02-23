package com.atlasia.ai.persistence;

import com.atlasia.ai.model.GraftExecutionEntity;
import com.atlasia.ai.model.GraftExecutionEntity.GraftExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GraftExecutionRepository extends JpaRepository<GraftExecutionEntity, UUID> {

    List<GraftExecutionEntity> findByRunId(UUID runId);

    List<GraftExecutionEntity> findByRunIdAndStatus(UUID runId, GraftExecutionStatus status);

    List<GraftExecutionEntity> findByAgentName(String agentName);

    List<GraftExecutionEntity> findByStatus(GraftExecutionStatus status);

    @Query("SELECT g FROM GraftExecutionEntity g WHERE g.runId = :runId AND g.checkpointAfter = :checkpointAfter")
    List<GraftExecutionEntity> findByRunIdAndCheckpointAfter(
            @Param("runId") UUID runId,
            @Param("checkpointAfter") String checkpointAfter);

    @Query("SELECT g FROM GraftExecutionEntity g WHERE g.agentName = :agentName AND g.status = 'FAILED' AND g.startedAt > :since")
    List<GraftExecutionEntity> findRecentFailuresByAgent(
            @Param("agentName") String agentName,
            @Param("since") Instant since);

    @Query("SELECT COUNT(g) FROM GraftExecutionEntity g WHERE g.agentName = :agentName AND g.status = 'COMPLETED'")
    long countSuccessfulExecutionsByAgent(@Param("agentName") String agentName);

    @Query("SELECT COUNT(g) FROM GraftExecutionEntity g WHERE g.agentName = :agentName AND g.status = 'FAILED'")
    long countFailedExecutionsByAgent(@Param("agentName") String agentName);
}
