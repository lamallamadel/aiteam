package com.atlasia.ai.persistence;

import com.atlasia.ai.model.AuditDataMutationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditDataMutationRepository extends JpaRepository<AuditDataMutationEntity, UUID> {
    List<AuditDataMutationEntity> findByUserIdOrderByTimestampDesc(UUID userId);
    
    List<AuditDataMutationEntity> findByEntityTypeAndEntityIdOrderByTimestampDesc(
        String entityType, String entityId);
    
    List<AuditDataMutationEntity> findByTimestampBetweenOrderByTimestampAsc(Instant start, Instant end);
    
    List<AuditDataMutationEntity> findByUserIdAndTimestampBetweenOrderByTimestampAsc(
        UUID userId, Instant start, Instant end);
    
    @Query(value = "SELECT * FROM audit_data_mutations ORDER BY timestamp DESC LIMIT 1", nativeQuery = true)
    Optional<AuditDataMutationEntity> findLatestEvent();
}
