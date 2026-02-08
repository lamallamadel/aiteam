package com.atlasia.ai.persistence;

import com.atlasia.ai.model.RunArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RunArtifactRepository extends JpaRepository<RunArtifactEntity, UUID> {

    List<RunArtifactEntity> findByAgentName(String agentName);

    List<RunArtifactEntity> findByArtifactType(String artifactType);

    @Query("SELECT a FROM RunArtifactEntity a WHERE a.artifactType = :artifactType AND a.createdAt BETWEEN :start AND :end")
    List<RunArtifactEntity> findByArtifactTypeAndDateRange(
            @Param("artifactType") String artifactType,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("SELECT a FROM RunArtifactEntity a WHERE a.agentName = :agentName AND a.createdAt BETWEEN :start AND :end")
    List<RunArtifactEntity> findByAgentNameAndDateRange(
            @Param("agentName") String agentName,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("SELECT a FROM RunArtifactEntity a JOIN a.run r WHERE r.id = :runId")
    List<RunArtifactEntity> findByRunId(@Param("runId") UUID runId);

    @Query("SELECT COUNT(a) FROM RunArtifactEntity a WHERE a.artifactType = :artifactType")
    long countByArtifactType(@Param("artifactType") String artifactType);

    @Query("SELECT a.agentName as agentName, COUNT(a) as count FROM RunArtifactEntity a GROUP BY a.agentName")
    List<Object[]> countByAgentName();
}
