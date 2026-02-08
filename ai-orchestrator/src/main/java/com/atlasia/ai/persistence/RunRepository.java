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

       List<RunEntity> findByRepo(String repo);

       List<RunEntity> findByRepoAndCreatedAtBetween(String repo, Instant start, Instant end);

       List<RunEntity> findByCreatedAtBetween(Instant start, Instant end);

       @Query("SELECT r FROM RunEntity r WHERE r.status = :status AND r.repo = :repo")
       List<RunEntity> findByStatusAndRepo(@Param("status") RunStatus status, @Param("repo") String repo);

       @Query("SELECT r FROM RunEntity r WHERE r.status = :status AND r.repo = :repo AND r.createdAt BETWEEN :start AND :end")
       List<RunEntity> findByStatusAndRepoAndCreatedAtBetween(
                     @Param("status") RunStatus status,
                     @Param("repo") String repo,
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT r.repo as repo, COUNT(r) as count FROM RunEntity r GROUP BY r.repo")
       List<Map<String, Object>> countByRepo();

       @Query("SELECT r.status as status, COUNT(r) as count FROM RunEntity r GROUP BY r.status")
       List<Map<String, Object>> countByStatus();

       @Query("SELECT r.repo as repo, r.status as status, COUNT(r) as count FROM RunEntity r GROUP BY r.repo, r.status")
       List<Map<String, Object>> countByStatusGroupByRepo();

       @Query("SELECT r.repo as repo, r.status as status, COUNT(r) as count FROM RunEntity r WHERE r.createdAt BETWEEN :start AND :end GROUP BY r.repo, r.status")
       List<Map<String, Object>> countByStatusGroupByRepoAndDateRange(
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT r FROM RunEntity r JOIN r.artifacts a WHERE r.status = 'ESCALATED' AND a.artifactType = 'escalation.json'")
       List<RunEntity> findEscalatedRunsWithArtifacts();

       @Query("SELECT r FROM RunEntity r WHERE r.ciFixCount > :threshold OR r.e2eFixCount > :threshold")
       List<RunEntity> findRunsWithHighFixCount(@Param("threshold") int threshold);

       @Query("SELECT r FROM RunEntity r WHERE r.ciFixCount > :threshold")
       List<RunEntity> findRunsWithHighCiFixCount(@Param("threshold") int threshold);

       @Query("SELECT r FROM RunEntity r WHERE r.e2eFixCount > :threshold")
       List<RunEntity> findRunsWithHighE2eFixCount(@Param("threshold") int threshold);

       @Query("SELECT r.status as status, AVG(r.ciFixCount) as avgCiFix, AVG(r.e2eFixCount) as avgE2eFix FROM RunEntity r GROUP BY r.status")
       List<Map<String, Object>> getAverageFixCountsByStatus();

       @Query("SELECT r.repo as repo, AVG(r.ciFixCount) as avgCiFix, AVG(r.e2eFixCount) as avgE2eFix FROM RunEntity r GROUP BY r.repo")
       List<Map<String, Object>> getAverageFixCountsByRepo();

       @Query("SELECT AVG(r.ciFixCount) as avgCiFix, AVG(r.e2eFixCount) as avgE2eFix FROM RunEntity r WHERE r.createdAt BETWEEN :start AND :end")
       Map<String, Object> getAverageFixCountsByDateRange(
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT r.repo as repo, " +
                     "COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) as successCount, " +
                     "COUNT(r) as totalCount, " +
                     "CAST(COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) AS double) / COUNT(r) as successRate " +
                     "FROM RunEntity r GROUP BY r.repo")
       List<Map<String, Object>> calculateSuccessRateByRepo();

       @Query("SELECT " +
                     "COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) as successCount, " +
                     "COUNT(r) as totalCount, " +
                     "CAST(COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) AS double) / COUNT(r) as successRate " +
                     "FROM RunEntity r WHERE r.createdAt BETWEEN :start AND :end")
       Map<String, Object> calculateSuccessRateByDateRange(
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT r.repo as repo, " +
                     "COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) as successCount, " +
                     "COUNT(r) as totalCount, " +
                     "CAST(COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) AS double) / COUNT(r) as successRate " +
                     "FROM RunEntity r WHERE r.createdAt BETWEEN :start AND :end GROUP BY r.repo")
       List<Map<String, Object>> calculateSuccessRateByRepoAndDateRange(
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT a.agentName as agentName, " +
                     "COUNT(DISTINCT r.id) as totalRuns, " +
                     "COUNT(DISTINCT CASE WHEN r.status = 'DONE' THEN r.id END) as successfulRuns, " +
                     "CAST(COUNT(DISTINCT CASE WHEN r.status = 'DONE' THEN r.id END) AS double) / COUNT(DISTINCT r.id) as successRate, "
                     +
                     "AVG(r.ciFixCount) as avgCiFix, " +
                     "AVG(r.e2eFixCount) as avgE2eFix " +
                     "FROM RunArtifactEntity a JOIN a.run r GROUP BY a.agentName")
       List<Map<String, Object>> getAgentPerformanceMetrics();

       @Query("SELECT a.agentName as agentName, " +
                     "COUNT(DISTINCT r.id) as totalRuns, " +
                     "COUNT(DISTINCT CASE WHEN r.status = 'DONE' THEN r.id END) as successfulRuns, " +
                     "CAST(COUNT(DISTINCT CASE WHEN r.status = 'DONE' THEN r.id END) AS double) / COUNT(DISTINCT r.id) as successRate, "
                     +
                     "AVG(r.ciFixCount) as avgCiFix, " +
                     "AVG(r.e2eFixCount) as avgE2eFix " +
                     "FROM RunArtifactEntity a JOIN a.run r WHERE r.createdAt BETWEEN :start AND :end GROUP BY a.agentName")
       List<Map<String, Object>> getAgentPerformanceMetricsByDateRange(
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT a.agentName as agentName, " +
                     "COUNT(DISTINCT r.id) as totalRuns, " +
                     "COUNT(DISTINCT CASE WHEN r.status = 'DONE' THEN r.id END) as successfulRuns, " +
                     "CAST(COUNT(DISTINCT CASE WHEN r.status = 'DONE' THEN r.id END) AS double) / COUNT(DISTINCT r.id) as successRate, "
                     +
                     "AVG(r.ciFixCount) as avgCiFix, " +
                     "AVG(r.e2eFixCount) as avgE2eFix " +
                     "FROM RunArtifactEntity a JOIN a.run r WHERE r.repo = :repo GROUP BY a.agentName")
       List<Map<String, Object>> getAgentPerformanceMetricsByRepo(@Param("repo") String repo);

       @Query("SELECT r.currentAgent as agentName, " +
                     "COUNT(r) as totalRuns, " +
                     "AVG(r.ciFixCount) as avgCiFix, " +
                     "AVG(r.e2eFixCount) as avgE2eFix " +
                     "FROM RunEntity r WHERE r.currentAgent IS NOT NULL GROUP BY r.currentAgent")
       List<Map<String, Object>> getCurrentAgentPerformanceMetrics();

       @Query("SELECT r.mode as mode, " +
                     "COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) as successCount, " +
                     "COUNT(r) as totalCount, " +
                     "CAST(COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) AS double) / COUNT(r) as successRate, " +
                     "AVG(r.ciFixCount) as avgCiFix, " +
                     "AVG(r.e2eFixCount) as avgE2eFix " +
                     "FROM RunEntity r GROUP BY r.mode")
       List<Map<String, Object>> getMetricsByMode();

       @Query("SELECT r.repo as repo, " +
                     "COUNT(CASE WHEN r.status = 'ESCALATED' THEN 1 END) as escalatedCount, " +
                     "COUNT(r) as totalCount, " +
                     "CAST(COUNT(CASE WHEN r.status = 'ESCALATED' THEN 1 END) AS double) / COUNT(r) as escalationRate "
                     +
                     "FROM RunEntity r GROUP BY r.repo")
       List<Map<String, Object>> calculateEscalationRateByRepo();

       @Query("SELECT r FROM RunEntity r WHERE r.status = 'ESCALATED' AND r.createdAt BETWEEN :start AND :end")
       List<RunEntity> findEscalatedRunsByDateRange(
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT r FROM RunEntity r WHERE r.ciFixCount + r.e2eFixCount > :totalThreshold")
       List<RunEntity> findRunsWithHighTotalFixCount(@Param("totalThreshold") int totalThreshold);

       @Query("SELECT SUM(r.ciFixCount) as totalCiFix, SUM(r.e2eFixCount) as totalE2eFix FROM RunEntity r WHERE r.createdAt BETWEEN :start AND :end")
       Map<String, Object> getTotalFixCountsByDateRange(
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT r.repo as repo, SUM(r.ciFixCount) as totalCiFix, SUM(r.e2eFixCount) as totalE2eFix FROM RunEntity r GROUP BY r.repo")
       List<Map<String, Object>> getTotalFixCountsByRepo();

       @Query("SELECT r FROM RunEntity r WHERE r.status IN :statuses AND r.createdAt BETWEEN :start AND :end")
       List<RunEntity> findByStatusInAndCreatedAtBetween(
                     @Param("statuses") List<RunStatus> statuses,
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT r.status as status, r.repo as repo, " +
                     "AVG(r.ciFixCount) as avgCiFix, " +
                     "AVG(r.e2eFixCount) as avgE2eFix, " +
                     "COUNT(r) as totalCount " +
                     "FROM RunEntity r WHERE r.createdAt BETWEEN :start AND :end GROUP BY r.status, r.repo")
       List<Map<String, Object>> getDetailedMetricsByStatusAndRepo(
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT r FROM RunEntity r WHERE r.status = :status AND r.repo IN :repos AND r.createdAt BETWEEN :start AND :end")
       List<RunEntity> findByStatusAndRepoInAndCreatedAtBetween(
                     @Param("status") RunStatus status,
                     @Param("repos") List<String> repos,
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT r.currentAgent as agentName, " +
                     "r.status as status, " +
                     "COUNT(r) as count, " +
                     "AVG(r.ciFixCount) as avgCiFix, " +
                     "AVG(r.e2eFixCount) as avgE2eFix " +
                     "FROM RunEntity r WHERE r.currentAgent IS NOT NULL GROUP BY r.currentAgent, r.status")
       List<Map<String, Object>> getAgentStatusDistribution();

       @Query("SELECT r.mode as mode, " +
                     "r.repo as repo, " +
                     "COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) as successCount, " +
                     "COUNT(r) as totalCount, " +
                     "CAST(COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) AS double) / COUNT(r) as successRate " +
                     "FROM RunEntity r GROUP BY r.mode, r.repo")
       List<Map<String, Object>> calculateSuccessRateByModeAndRepo();

       @Query("SELECT r FROM RunEntity r WHERE r.ciFixCount = 0 AND r.e2eFixCount = 0 AND r.status = 'DONE'")
       List<RunEntity> findSuccessfulRunsWithoutFixes();

       @Query("SELECT r.repo as repo, " +
                     "AVG(r.ciFixCount + r.e2eFixCount) as avgTotalFixes, " +
                     "MAX(r.ciFixCount + r.e2eFixCount) as maxTotalFixes, " +
                     "MIN(r.ciFixCount + r.e2eFixCount) as minTotalFixes " +
                     "FROM RunEntity r GROUP BY r.repo")
       List<Map<String, Object>> getFixCountStatisticsByRepo();

       @Query("SELECT r.status as status, " +
                     "AVG(CAST(timestampdiff(SECOND, r.createdAt, r.updatedAt) AS double)) as avgDurationSeconds, " +
                     "COUNT(r) as totalCount " +
                     "FROM RunEntity r GROUP BY r.status")
       List<Map<String, Object>> getAverageDurationByStatus();

       @Query("SELECT r.repo as repo, " +
                     "r.status as status, " +
                     "AVG(CAST(timestampdiff(SECOND, r.createdAt, r.updatedAt) AS double)) as avgDurationSeconds " +
                     "FROM RunEntity r WHERE r.createdAt BETWEEN :start AND :end GROUP BY r.repo, r.status")
       List<Map<String, Object>> getAverageDurationByRepoAndStatus(
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT a.artifactType as artifactType, " +
                     "COUNT(DISTINCT a.run.id) as runCount, " +
                     "COUNT(a) as artifactCount " +
                     "FROM RunArtifactEntity a GROUP BY a.artifactType")
       List<Map<String, Object>> getArtifactTypeDistribution();

       @Query("SELECT r FROM RunEntity r WHERE SIZE(r.artifacts) > :minArtifactCount")
       List<RunEntity> findRunsWithMinimumArtifacts(@Param("minArtifactCount") int minArtifactCount);

       @Query("SELECT r.repo as repo, " +
                     "COUNT(DISTINCT r.currentAgent) as distinctAgents, " +
                     "COUNT(r) as totalRuns " +
                     "FROM RunEntity r WHERE r.currentAgent IS NOT NULL GROUP BY r.repo")
       List<Map<String, Object>> getAgentDiversityByRepo();

       @Query("SELECT r FROM RunEntity r JOIN r.artifacts a WHERE a.artifactType = :artifactType AND r.createdAt BETWEEN :start AND :end")
       List<RunEntity> findRunsByArtifactTypeAndDateRange(
                     @Param("artifactType") String artifactType,
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT r.status as fromStatus, r2.status as toStatus, COUNT(*) as transitionCount " +
                     "FROM RunEntity r JOIN RunEntity r2 ON r.repo = r2.repo AND r.issueNumber = r2.issueNumber " +
                     "WHERE r.updatedAt < r2.updatedAt GROUP BY r.status, r2.status")
       List<Map<String, Object>> getStatusTransitionCounts();

       @Query("SELECT r.repo as repo, " +
                     "r.issueNumber as issueNumber, " +
                     "COUNT(r) as attemptCount, " +
                     "MAX(r.updatedAt) as lastAttempt " +
                     "FROM RunEntity r GROUP BY r.repo, r.issueNumber HAVING COUNT(r) > 1")
       List<Map<String, Object>> findRetriedIssues();

       @Query("SELECT DATE(r.createdAt) as date, " +
                     "COUNT(r) as runCount, " +
                     "COUNT(CASE WHEN r.status = 'DONE' THEN 1 END) as successCount, " +
                     "COUNT(CASE WHEN r.status = 'ESCALATED' THEN 1 END) as escalatedCount, " +
                     "COUNT(CASE WHEN r.status = 'FAILED' THEN 1 END) as failedCount " +
                     "FROM RunEntity r WHERE r.createdAt BETWEEN :start AND :end GROUP BY DATE(r.createdAt) ORDER BY DATE(r.createdAt)")
       List<Map<String, Object>> getDailyRunStatistics(
                     @Param("start") Instant start,
                     @Param("end") Instant end);

       @Query("SELECT a.agentName as agentName, " +
                     "r.repo as repo, " +
                     "COUNT(DISTINCT r.id) as totalRuns, " +
                     "AVG(r.ciFixCount) as avgCiFix, " +
                     "AVG(r.e2eFixCount) as avgE2eFix, " +
                     "COUNT(DISTINCT CASE WHEN r.status = 'DONE' THEN r.id END) as successfulRuns " +
                     "FROM RunArtifactEntity a JOIN a.run r GROUP BY a.agentName, r.repo")
       List<Map<String, Object>> getAgentPerformanceByRepoDetailed();

       @Query("SELECT r.repo as repo, " +
                     "r.mode as mode, " +
                     "COUNT(CASE WHEN r.status = 'FAILED' THEN 1 END) as failureCount, " +
                     "COUNT(r) as totalCount, " +
                     "CAST(COUNT(CASE WHEN r.status = 'FAILED' THEN 1 END) AS double) / COUNT(r) as failureRate " +
                     "FROM RunEntity r GROUP BY r.repo, r.mode")
       List<Map<String, Object>> calculateFailureRateByRepoAndMode();

       @Query("SELECT r FROM RunEntity r WHERE r.status NOT IN ('DONE', 'FAILED', 'ESCALATED')")
       List<RunEntity> findInProgressRuns();

       @Query("SELECT r FROM RunEntity r WHERE r.status = :status ORDER BY r.updatedAt DESC")
       List<RunEntity> findByStatusOrderByUpdatedAtDesc(@Param("status") RunStatus status);

       @Query("SELECT r FROM RunEntity r WHERE r.repo = :repo ORDER BY (r.ciFixCount + r.e2eFixCount) DESC")
       List<RunEntity> findByRepoOrderByTotalFixCountDesc(@Param("repo") String repo);
}
