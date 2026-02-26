package com.atlasia.ai.persistence;

import com.atlasia.ai.model.AuditAccessLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditAccessLogRepository extends JpaRepository<AuditAccessLogEntity, UUID> {
    List<AuditAccessLogEntity> findByUserIdOrderByTimestampDesc(UUID userId);
    
    List<AuditAccessLogEntity> findByResourceTypeAndResourceIdOrderByTimestampDesc(
        String resourceType, String resourceId);
    
    List<AuditAccessLogEntity> findByTimestampBetweenOrderByTimestampAsc(Instant start, Instant end);
    
    List<AuditAccessLogEntity> findByUserIdAndTimestampBetweenOrderByTimestampAsc(
        UUID userId, Instant start, Instant end);
    
    @Query(value = "SELECT * FROM audit_access_logs ORDER BY timestamp DESC LIMIT 1", nativeQuery = true)
    Optional<AuditAccessLogEntity> findLatestEvent();
}
