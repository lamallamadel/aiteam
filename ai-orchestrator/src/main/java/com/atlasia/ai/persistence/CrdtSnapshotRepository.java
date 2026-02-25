package com.atlasia.ai.persistence;

import com.atlasia.ai.model.CrdtSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrdtSnapshotRepository extends JpaRepository<CrdtSnapshotEntity, UUID> {
    
    @Query("SELECT s FROM CrdtSnapshotEntity s WHERE s.runId = :runId ORDER BY s.createdAt DESC")
    List<CrdtSnapshotEntity> findByRunIdOrderByCreatedAtDesc(UUID runId);
    
    @Query("SELECT s FROM CrdtSnapshotEntity s WHERE s.runId = :runId ORDER BY s.createdAt DESC LIMIT 1")
    Optional<CrdtSnapshotEntity> findLatestByRunId(UUID runId);
}
