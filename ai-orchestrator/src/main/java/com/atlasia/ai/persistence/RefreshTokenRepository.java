package com.atlasia.ai.persistence;

import com.atlasia.ai.model.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    
    List<RefreshTokenEntity> findByUserId(UUID userId);
    
    List<RefreshTokenEntity> findByUserIdAndRevokedFalse(UUID userId);
    
    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.userId = :userId AND r.revoked = false")
    int revokeAllByUserId(UUID userId);
    
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :now")
    int deleteExpiredTokens(Instant now);
    
    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.tokenHash = :tokenHash")
    int revokeByTokenHash(String tokenHash);
}
