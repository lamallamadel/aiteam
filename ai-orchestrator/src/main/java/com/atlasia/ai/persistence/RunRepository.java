package com.atlasia.ai.persistence;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RunRepository extends JpaRepository<RunEntity, UUID> {

    List<RunEntity> findByStatusAndCreatedAtBetween(RunStatus status, Instant start, Instant end);

    @Query("SELECT r.repo as repo, COUNT(r) as count FROM RunEntity r GROUP BY r.repo")
    List<Map<String, Object>> countByRepo();

    @Query("SELECT r.status as status, COUNT(r) as count FROM RunEntity r GROUP BY r.status")
    List<Map<String, Object>> countByStatus();

    @Query("SELECT r FROM RunEntity r JOIN r.artifacts a WHERE r.status = 'ESCALATED' AND a.artifactType = 'escalation.json'")
    List<RunEntity> findEscalatedRunsWithArtifacts();

    @Query("SELECT r FROM RunEntity r WHERE r.ciFixCount > :threshold OR r.e2eFixCount > :threshold")
    List<RunEntity> findRunsWithHighFixCount(@Param("threshold") int threshold);

    @Query("SELECT r.status as status, AVG(r.ciFixCount) as avgCiFix, AVG(r.e2eFixCount) as avgE2eFix FROM RunEntity r GROUP BY r.status")
    List<Map<String, Object>> getAverageFixCountsByStatus();
}
