package com.atlasia.ai.persistence;

import com.atlasia.ai.model.PasswordHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntity, UUID> {
    
    @Query("SELECT ph FROM PasswordHistoryEntity ph WHERE ph.userId = :userId ORDER BY ph.createdAt DESC")
    List<PasswordHistoryEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    @Query(value = "SELECT ph FROM PasswordHistoryEntity ph WHERE ph.userId = :userId ORDER BY ph.createdAt DESC LIMIT :limit")
    List<PasswordHistoryEntity> findTopNByUserIdOrderByCreatedAtDesc(UUID userId, int limit);
}
