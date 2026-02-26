package com.atlasia.ai.persistence;

import com.atlasia.ai.model.AuditAdminActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditAdminActionRepository extends JpaRepository<AuditAdminActionEntity, UUID> {
    List<AuditAdminActionEntity> findByAdminUserIdOrderByTimestampDesc(UUID adminUserId);
    
    List<AuditAdminActionEntity> findByTargetUserIdOrderByTimestampDesc(UUID targetUserId);
    
    List<AuditAdminActionEntity> findByTimestampBetweenOrderByTimestampAsc(Instant start, Instant end);
    
    @Query(value = "SELECT * FROM audit_admin_actions ORDER BY timestamp DESC LIMIT 1", nativeQuery = true)
    Optional<AuditAdminActionEntity> findLatestEvent();
}
