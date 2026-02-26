package com.atlasia.ai.persistence;

import com.atlasia.ai.model.AuditAuthenticationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditAuthenticationEventRepository extends JpaRepository<AuditAuthenticationEventEntity, UUID> {
    List<AuditAuthenticationEventEntity> findByUserIdOrderByTimestampDesc(UUID userId);
    
    List<AuditAuthenticationEventEntity> findByUsernameOrderByTimestampDesc(String username);
    
    List<AuditAuthenticationEventEntity> findByTimestampBetweenOrderByTimestampAsc(Instant start, Instant end);
    
    List<AuditAuthenticationEventEntity> findByUserIdAndTimestampBetweenOrderByTimestampAsc(
        UUID userId, Instant start, Instant end);
    
    @Query("SELECT e FROM AuditAuthenticationEventEntity e WHERE e.archivedAt IS NULL ORDER BY e.timestamp DESC")
    List<AuditAuthenticationEventEntity> findLatestEventForHashChain();
    
    @Query(value = "SELECT * FROM audit_authentication_events ORDER BY timestamp DESC LIMIT 1", nativeQuery = true)
    Optional<AuditAuthenticationEventEntity> findLatestEvent();
}
